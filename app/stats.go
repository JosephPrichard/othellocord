package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/jmoiron/sqlx"
	"log/slog"
	"math"

	"golang.org/x/sync/errgroup"
)

type StatsRow struct {
	PlayerID string  `db:"player_id"`
	Elo      float64 `db:"elo"`
	Won      int     `db:"won"`
	Drawn    int     `db:"drawn"`
	Lost     int     `db:"lost"`
}

type Stats struct {
	Player Player
	Elo    float64
	Won    int
	Drawn  int
	Lost   int
}

func (s Stats) WinRate() string {
	wr := 0.0
	total := s.Won + s.Lost + s.Drawn
	if total > 0 {
		wr = float64(s.Won) / float64(s.Won+s.Lost+s.Drawn)
	}
	return fmt.Sprintf("%%%0.2f", wr)
}

func DefaultStats(playerID string) StatsRow {
	return StatsRow{
		PlayerID: playerID,
		Elo:      1500,
		Won:      0,
		Drawn:    0,
		Lost:     0,
	}
}

func MapStats(row StatsRow) Stats {
	return Stats{
		Player: MakePlayer(row.PlayerID, ""),
		Elo:    row.Elo,
		Won:    row.Won,
		Drawn:  row.Drawn,
		Lost:   row.Lost,
	}
}

type StatsService struct {
	database  *sqlx.DB
	userCache *UserCache
}

func MakeStatsService(db *sqlx.DB, userCache *UserCache) *StatsService {
	return &StatsService{database: db, userCache: userCache}
}

func getStats(ctx context.Context, querier Querier, playerID string) (StatsRow, error) {
	return getStatsDefault(ctx, querier, DefaultStats(playerID))
}

func getStatsDefault(ctx context.Context, querier Querier, defaultStats StatsRow) (StatsRow, error) {
	trace := ctx.Value(TraceKey)

	isCreated := false

	var stats StatsRow
	err := querier.GetContext(ctx, &stats, "SELECT player_id, elo, won, lost, drawn FROM stats WHERE player_id = $1;", defaultStats.PlayerID)
	if errors.Is(err, sql.ErrNoRows) {
		isCreated = true
	} else if err != nil {
		return StatsRow{}, fmt.Errorf("failed to get stats: %w", err)
	}

	if isCreated {
		stats = defaultStats
		if _, err = querier.ExecContext(ctx,
			"INSERT INTO STATS (player_id, elo, won, lost, drawn) VALUES ($1, $2, $3, $4, $5)",
			stats.PlayerID, stats.Elo, stats.Won, stats.Lost, stats.Drawn,
		); err != nil {
			return stats, fmt.Errorf("failed to insert stats %+v: %w", stats, err)
		}
	}

	slog.Info("selected stats for player", "trace", trace, "playerID", stats.PlayerID, "stats", stats, "created", isCreated)
	return stats, nil
}

func getTopStats(ctx context.Context, db *sqlx.DB, count int) ([]StatsRow, error) {
	trace := ctx.Value(TraceKey)

	var stats []StatsRow
	err := db.SelectContext(ctx, &stats, "SELECT player_id, elo, won, lost, drawn FROM stats ORDER BY elo DESC LIMIT $1;", count)
	if err != nil {
		return nil, fmt.Errorf("failed to get top stats: %w", err)
	}

	slog.Info("selected top stats", "trace", trace, "stats", stats)
	return stats, nil
}

func updateOneStats(ctx context.Context, querier Querier, stats StatsRow) error {
	_, err := querier.ExecContext(ctx,
		"UPDATE stats SET elo = ?, won = ?, lost = ?, drawn = ? WHERE player_id = ?;",
		stats.Elo, stats.Won, stats.Lost, stats.Drawn, stats.PlayerID,
	)
	return err
}

type StatsResult struct {
	WinnerElo float64
	LoserElo  float64
	WinDiff   float64
	LoseDiff  float64
}

func formatElo(elo float64) string {
	prefix := ""
	if elo >= 0 {
		prefix = "+"
	}
	return fmt.Sprintf("%s%.2f", prefix, elo)
}

func (s StatsResult) FormatWinnerEloDiff() string {
	return formatElo(s.WinDiff)
}

func (s StatsResult) FormatLoserEloDiff() string {
	return formatElo(s.LoseDiff)
}

func UpdateStats(ctx context.Context, querier Querier, gameResult GameResult) (StatsResult, error) {
	trace := ctx.Value(TraceKey)

	winner, err := getStats(ctx, querier, gameResult.Winner.ID)
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get winner stats: %w", err)
	}
	loser, err := getStats(ctx, querier, gameResult.Loser.ID)
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get loser stats: %w", err)
	}

	if gameResult.IsDraw || gameResult.Winner.ID == gameResult.Loser.ID {
		return StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinDiff: 0, LoseDiff: 0}, nil
	}

	winBefore := winner.Elo
	lossBefore := loser.Elo
	winner.Elo = calcEloWon(winner.Elo, probability(loser.Elo, winner.Elo))
	loser.Elo = calcEloLost(loser.Elo, probability(winner.Elo, loser.Elo))
	winner.Won++
	loser.Lost++

	if err := updateOneStats(ctx, querier, winner); err != nil {
		return StatsResult{}, fmt.Errorf("failed to update winner stat: %w", err)
	}
	if err := updateOneStats(ctx, querier, loser); err != nil {
		return StatsResult{}, fmt.Errorf("failed to update loser stat: %w", err)
	}

	winDiff := winner.Elo - winBefore
	lossDiff := loser.Elo - lossBefore
	statsResult := StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinDiff: winDiff, LoseDiff: lossDiff}

	slog.Info("updated stats tx executed", "trace", trace, "game", gameResult, "stats", statsResult)
	return statsResult, nil
}

const EloK = 30

func probability(rating1, rating2 float64) float64 {
	return 1.0 / (1.0 + math.Pow(10, (rating1-rating2)/400.0))
}

func calcEloWon(rating, probability float64) float64 {
	return rating + EloK*(1.0-probability)
}

func calcEloLost(rating, probability float64) float64 {
	return rating - EloK*probability
}

func (service *StatsService) ReadStats(ctx context.Context, playerID string) (Stats, error) {
	row, err := getStats(ctx, service.database, playerID)
	if err != nil {
		return Stats{}, fmt.Errorf("failed to next row: %w", err)
	}
	stats := MapStats(row)

	if stats.Player.IsHuman() {
		name, err := service.userCache.GetUsername(ctx, playerID)
		if err != nil {
			return Stats{}, fmt.Errorf("failed to get username: %w", err)
		}
		stats.Player.Name = name
	}
	return stats, nil
}

func (service *StatsService) ReadTopStats(ctx context.Context, count int) ([]Stats, error) {
	trace := ctx.Value(TraceKey)

	rowList, err := getTopStats(ctx, service.database, count)
	if err != nil {
		return nil, fmt.Errorf("failed to next top stats: %w", err)
	}

	eg, ctx := errgroup.WithContext(ctx)
	statsList := make([]Stats, len(rowList))

	for i, row := range rowList {
		stats := &statsList[i]
		*stats = MapStats(row)

		if stats.Player.IsBot() {
			continue
		}

		eg.Go(func() error {
			username, err := service.userCache.GetUsername(ctx, stats.Player.ID)
			if err != nil {
				return fmt.Errorf("failed in get user task: %d: %w", i, err)
			}
			stats.Player.Name = username
			return nil
		})
	}
	if err := eg.Wait(); err != nil {
		return nil, err
	}

	slog.Info("fetched top stats", "trace", trace, "count", count)
	return statsList, nil
}
