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

func GetOrInsertStats(ctx context.Context, q QuerierContext, playerID string) (StatsRow, error) {
	return GetOrInsertStatsDefault(ctx, q, DefaultStats(playerID))
}

func GetOrInsertStatsDefault(ctx context.Context, q QuerierContext, defaultStats StatsRow) (StatsRow, error) {
	trace := ctx.Value(TraceKey)

	fail := func(err error) (StatsRow, error) {
		slog.Error("failed to get or insert top stats", "trace", trace, "playerID", defaultStats.PlayerID, "err", err)
		return StatsRow{}, err
	}

	var stats StatsRow
	createStats := false

	err := q.GetContext(ctx, &stats, "SELECT player_id, elo, won, lost, drawn FROM stats WHERE player_id = $1;", defaultStats.PlayerID)

	if errors.Is(err, sql.ErrNoRows) {
		createStats = true
	} else if err != nil {
		return fail(err)
	}

	if createStats {
		stats = defaultStats
		slog.Info("inserting stats", "trace", trace, "stats", stats)

		_, err := q.ExecContext(ctx,
			"INSERT INTO STATS (player_id, elo, won, lost, drawn) VALUES ($1, $2, $3, $4, $5)",
			stats.PlayerID, stats.Elo, stats.Won, stats.Lost, stats.Drawn,
		)
		if err != nil {
			return fail(err)
		}
	}

	slog.Info("selected stats for player", "trace", trace, "playerID", stats.PlayerID, "stats", stats, "created", createStats)
	return stats, nil
}

func GetTopStats(ctx context.Context, db *sqlx.DB, count int) ([]StatsRow, error) {
	trace := ctx.Value(TraceKey)

	var stats []StatsRow
	err := db.SelectContext(ctx, &stats, "SELECT player_id, elo, won, lost, drawn FROM stats ORDER BY elo DESC LIMIT $1;", count)
	if err != nil {
		slog.Error("failed to get top stats", "trace", trace, "err", err)
		return nil, err
	}

	slog.Info("selected top stats", "trace", trace, "stats", stats)
	return stats, nil
}

func updateStat(ctx context.Context, q QuerierContext, stats StatsRow) error {
	_, err := q.ExecContext(ctx,
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

func UpdateStats(ctx context.Context, q QuerierContext, gr GameResult) (StatsResult, error) {
	trace := ctx.Value(TraceKey)

	fail := func(err error) (StatsResult, error) {
		slog.Error("failed to update stats", "trace", trace, "result", gr, "err", err)
		return StatsResult{}, err
	}

	winner, err := GetOrInsertStats(ctx, q, gr.Winner.ID)
	if err != nil {
		return fail(fmt.Errorf("failed to get winner stats: %w", err))
	}
	loser, err := GetOrInsertStats(ctx, q, gr.Loser.ID)
	if err != nil {
		return fail(fmt.Errorf("failed to get loser stats: %w", err))
	}

	if gr.IsDraw || gr.Winner.ID == gr.Loser.ID {
		return StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinDiff: 0, LoseDiff: 0}, nil
	}

	winBefore := winner.Elo
	lossBefore := loser.Elo
	winner.Elo = calcEloWon(winner.Elo, probability(loser.Elo, winner.Elo))
	loser.Elo = calcEloLost(loser.Elo, probability(winner.Elo, loser.Elo))
	winner.Won++
	loser.Lost++

	if err := updateStat(ctx, q, winner); err != nil {
		return fail(fmt.Errorf("failed to update winner stat: %w", err))
	}
	if err := updateStat(ctx, q, loser); err != nil {
		return fail(fmt.Errorf("failed to update loser stat: %w", err))
	}

	winDiff := winner.Elo - winBefore
	lossDiff := loser.Elo - lossBefore
	sr := StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinDiff: winDiff, LoseDiff: lossDiff}

	slog.Info("updated stats tx executed", "trace", trace, "game", gr, "stats", sr)
	return sr, nil
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

func ReadStats(ctx context.Context, db *sqlx.DB, uc UserCacheApi, playerID string) (Stats, error) {
	row, err := GetOrInsertStats(ctx, db, playerID)
	if err != nil {
		return Stats{}, fmt.Errorf("failed to read row: %w", err)
	}
	stats := MapStats(row)

	if stats.Player.IsHuman() {
		if stats.Player.Name, err = uc.GetUsername(ctx, playerID); err != nil {
			return Stats{}, fmt.Errorf("failed to get username: %w", err)
		}
	}
	return stats, nil
}

func ReadTopStats(ctx context.Context, db *sqlx.DB, uc UserCacheApi, count int) ([]Stats, error) {
	trace := ctx.Value(TraceKey)

	rowList, err := GetTopStats(ctx, db, count)
	if err != nil {
		return nil, fmt.Errorf("failed to read top stats: %w", err)
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
			username, err := uc.GetUsername(ctx, stats.Player.ID)
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
