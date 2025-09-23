package app

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"math"

	"golang.org/x/sync/errgroup"
)

type StatsRow struct {
	PlayerID string
	Elo      float64
	Won      int
	Drawn    int
	Lost     int
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

func GetOrInsertStats(ctx context.Context, q Query, playerID string) (StatsRow, error) {
	return GetOrInsertStatsDefault(ctx, q, DefaultStats(playerID))
}

func GetOrInsertStatsDefault(ctx context.Context, q Query, defaultStats StatsRow) (StatsRow, error) {
	trace := ctx.Value(TraceKey)

	rows, err := q.Query("SELECT player_id, elo, won, lost, drawn FROM stats WHERE player_id = $1;", defaultStats.PlayerID)
	if err != nil {
		slog.Error("failed to get stats", "trace", trace, "playerID", defaultStats.PlayerID, "err", err)
		return StatsRow{}, err
	}
	defer rows.Close()

	var stats StatsRow
	if rows.Next() {
		if err := rows.Scan(&stats.PlayerID, &stats.Elo, &stats.Won, &stats.Lost, &stats.Drawn); err != nil {
			slog.Error("failed to scan stats", "trace", trace, "err", err)
			return StatsRow{}, err
		}
	} else {
		stats = defaultStats
		_, err := q.Exec("INSERT INTO STATS (player_id, elo, won, lost, drawn) VALUES ($1, $2, $3, $4, $5)",
			stats.PlayerID,
			stats.Elo,
			stats.Won,
			stats.Lost,
			stats.Drawn,
		)
		if err != nil {
			slog.Error("failed to insert stats", "trace", trace, "stats", stats, "err", err)
			return StatsRow{}, err
		}
		slog.Info("inserted stats", "trace", trace, "stats", stats)
	}

	slog.Info("selected stats for Player ID", "trace", trace, "playerID", stats.PlayerID, "stats", stats)
	return stats, nil
}

func GetTopStats(ctx context.Context, db *sql.DB, count int) ([]StatsRow, error) {
	trace := ctx.Value(TraceKey)

	rows, err := db.Query("SELECT player_id, elo, won, lost, drawn FROM stats ORDER BY elo DESC LIMIT $1;", count)
	if err != nil {
		slog.Error("failed to get top stats", "trace", trace, "err", err)
		return nil, err
	}
	defer rows.Close()

	var stats []StatsRow
	for rows.Next() {
		var stat StatsRow
		if err := rows.Scan(&stat.PlayerID, &stat.Elo, &stat.Won, &stat.Lost, &stat.Drawn); err != nil {
			slog.Error("failed to scan stats", "trace", trace, "err", err)
			return nil, err
		}
		stats = append(stats, stat)
	}

	slog.Info("selected top stats", "trace", trace, "stats", stats)
	return stats, nil
}

func updateStat(ctx context.Context, tx *sql.Tx, stats StatsRow) error {
	_, err := tx.Exec(
		"UPDATE stats SET elo = ?, won = ?, lost = ?, drawn = ? WHERE player_id = ?;",
		stats.Elo,
		stats.Won,
		stats.Lost,
		stats.Drawn,
		stats.PlayerID,
	)
	if err != nil {
		slog.Error("failed to exec update stats", "trace", ctx.Value(TraceKey), "stats", stats, "err", err)
		return err
	}
	return nil
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

func UpdateStats(ctx context.Context, db *sql.DB, gr GameResult) (StatsResult, error) {
	trace := ctx.Value(TraceKey)

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		slog.Error("failed to open update stats tx", "trace", trace, "err", err)
		return StatsResult{}, err
	}
	defer tx.Rollback()

	winner, err := GetOrInsertStats(ctx, tx, gr.Winner.ID)
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get winner stats: %s", err)
	}
	loser, err := GetOrInsertStats(ctx, tx, gr.Loser.ID)
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get loser stats: %s", err)
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

	if err := updateStat(ctx, tx, winner); err != nil {
		return StatsResult{}, fmt.Errorf("failed to update winner stat: %s", err)
	}
	if err := updateStat(ctx, tx, loser); err != nil {
		return StatsResult{}, fmt.Errorf("failed to update loser stat: %s", err)
	}

	if err := tx.Commit(); err != nil {
		slog.Error("failed to commit update stats tx", "trace", trace, "err", err)
		return StatsResult{}, err
	}

	winDiff := winner.Elo - winBefore
	lossDiff := loser.Elo - lossBefore
	sr := StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinDiff: winDiff, LoseDiff: lossDiff}

	slog.Info("updated stats tx executed", "trace", trace, "Game", gr, "stats", sr)
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

func ReadStats(ctx context.Context, db *sql.DB, uc UserCacheApi, playerID string) (Stats, error) {
	row, err := GetOrInsertStats(ctx, db, playerID)
	if err != nil {
		return Stats{}, fmt.Errorf("failed to read row: %s", err)
	}

	stats := MapStats(row)

	if stats.Player.IsHuman() {
		if stats.Player.Name, err = uc.GetUsername(ctx, playerID); err != nil {
			return Stats{}, fmt.Errorf("failed to read row: %s", err)
		}
	}

	return stats, nil
}

func ReadTopStats(ctx context.Context, db *sql.DB, uc UserCacheApi, count int) ([]Stats, error) {
	trace := ctx.Value(TraceKey)

	rowList, err := GetTopStats(ctx, db, count)
	if err != nil {
		return nil, fmt.Errorf("failed to read top rowList: %s", err)
	}

	eg, ctx := errgroup.WithContext(ctx)

	statsList := make([]Stats, len(rowList))

	for i, row := range rowList {
		stats := &statsList[i]
		*stats = MapStats(row)

		if stats.Player.IsBot() {
			continue
		}

		// fetch a user whose name cannot be calculated (it is not a bot)
		eg.Go(func() error {
			username, err := uc.GetUsername(ctx, stats.Player.ID)
			if err != nil {
				return fmt.Errorf("failed in get user task: %d: %s", i, err)
			}
			stats.Player.Name = username
			return nil
		})
	}

	if err := eg.Wait(); err != nil {
		slog.Error("error occurred in fetch username tasks", "trace", trace, "err", err)
		return nil, err
	}
	return statsList, nil
}
