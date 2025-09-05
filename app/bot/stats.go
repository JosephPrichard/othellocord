package bot

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"math"

	"golang.org/x/sync/errgroup"
)

var CreateSchema = "CREATE TABLE IF NOT EXISTS stats (player_id TEXT PRIMARY KEY, elo FLOAT, won INTEGER, drawn INTEGER, lost INTEGER);"

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

type Query interface {
	Query(query string, args ...any) (*sql.Rows, error)
	Exec(query string, args ...any) (sql.Result, error)
}

func DefaultStats(playerId string) Stats {
	return Stats{
		Player: Player{ID: playerId},
		Elo:    1500,
		Won:    0,
		Drawn:  0,
		Lost:   0,
	}
}

func GetOrInsertStats(ctx context.Context, q Query, playerId string, defaultStats Stats) (Stats, error) {
	trace := ctx.Value(TraceKey)

	rows, err := q.Query("SELECT player_id, elo, won, lost, drawn FROM stats WHERE player_id = ?;", playerId)
	if err != nil {
		slog.Error("failed to get stats", "trace", trace, "playerId", playerId, "err", err)
		return Stats{}, err
	}
	defer func() {
		_ = rows.Close()
	}()

	var stats Stats
	if rows.Next() {
		if err := rows.Scan(&stats.Player.ID, &stats.Elo, &stats.Won, &stats.Lost, &stats.Drawn); err != nil {
			slog.Error("failed to scan stats", "trace", trace, "err", err)
			return Stats{}, err
		}
	} else {
		stats = defaultStats
		_, err := q.Exec("INSERT INTO STATS (player_id, elo, won, lost, drawn) VALUES (?, ?, ?, ?, ?)",
			stats.Player.ID,
			stats.Elo,
			stats.Won,
			stats.Lost,
			stats.Drawn)
		if err != nil {
			slog.Error("failed to insert stats", "trace", trace, "stats", stats, "err", err)
			return Stats{}, err
		}
		slog.Info("inserted stats", "trace", trace, "stats", stats)
	}

	slog.Info("selected stats for Player ID", "trace", trace, "playerId", playerId, "stats", stats)
	return stats, nil
}

func GetTopStats(ctx context.Context, db *sql.DB, count int) ([]Stats, error) {
	trace := ctx.Value(TraceKey)

	rows, err := db.Query("SELECT player_id, elo, won, lost, drawn FROM stats ORDER BY elo DESC LIMIT ?;", count)
	if err != nil {
		slog.Error("failed to get top stats", "trace", trace, "err", err)
		return nil, err
	}
	defer func() {
		_ = rows.Close()
	}()

	var stats []Stats
	for rows.Next() {
		var stat Stats
		if err := rows.Scan(&stat.Player.ID, &stat.Elo, &stat.Won, &stat.Lost, &stat.Drawn); err != nil {
			slog.Error("failed to scan stats", "trace", trace, "err", err)
			return nil, err
		}
		stats = append(stats, stat)
	}

	slog.Info("selected top stats", "trace", trace, "stats", stats)
	return stats, nil
}

func updateStat(ctx context.Context, tx *sql.Tx, stats Stats) error {
	_, err := tx.Exec(
		"UPDATE stats SET elo = ?, won = ?, lost = ?, drawn = ? WHERE player_id = ?;",
		stats.Elo,
		stats.Won,
		stats.Lost,
		stats.Drawn,
		stats.Player.ID)
	if err != nil {
		slog.Error("failed to exec update stats", "trace", ctx.Value(TraceKey), "stats", stats, "err", err)
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
	defer func() {
		_ = tx.Rollback()
	}()

	winner, err := GetOrInsertStats(ctx, tx, gr.Winner.ID, DefaultStats(gr.Winner.ID))
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get winner stats: %w", err)
	}
	loser, err := GetOrInsertStats(ctx, tx, gr.Loser.ID, DefaultStats(gr.Loser.ID))
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get loser stats: %w", err)
	}

	if gr.IsDraw || gr.Winner.ID == gr.Loser.ID {
		sr := StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinDiff: 0, LoseDiff: 0}
		slog.Info("updating stats is a draw, noop", "trace", trace, "game", gr, "stats", sr)
		return sr, nil
	}

	winEloBefore := winner.Elo
	lossEloBefore := loser.Elo
	winner.Elo = eloWon(winner.Elo, probability(loser.Elo, winner.Elo))
	loser.Elo = eloLost(loser.Elo, probability(winner.Elo, loser.Elo))
	winner.Won++
	loser.Lost++

	if err := updateStat(ctx, tx, winner); err != nil {
		return StatsResult{}, fmt.Errorf("failed to update winner stat: %w", err)
	}
	if err := updateStat(ctx, tx, loser); err != nil {
		return StatsResult{}, fmt.Errorf("failed to update loser stat: %w", err)
	}

	if err := tx.Commit(); err != nil {
		slog.Error("failed to commit update stats tx", "trace", trace, "err", err)
		return StatsResult{}, err
	}

	winDiff := winner.Elo - winEloBefore
	lossDiff := loser.Elo - lossEloBefore
	sr := StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinDiff: winDiff, LoseDiff: lossDiff}

	slog.Info("updated stats tx executed", "trace", trace, "game", gr, "stats", sr)
	return sr, nil
}

const EloK = 30

func probability(rating1, rating2 float64) float64 {
	return 1.0 / (1.0 + math.Pow(10, (rating1-rating2)/400.0))
}

func eloWon(rating, probability float64) float64 {
	return rating + EloK*(1.0-probability)
}

func eloLost(rating, probability float64) float64 {
	return rating - EloK*probability
}

func ReadStats(ctx context.Context, db *sql.DB, uc UserCacheApi, playerId string) (Stats, error) {
	stats, err := GetOrInsertStats(ctx, db, playerId, DefaultStats(playerId))
	if err != nil {
		return Stats{}, fmt.Errorf("failed to read stats: %w", err)
	}
	stats.Player.Name, err = uc.GetUsername(ctx, playerId)
	if err != nil {
		return Stats{}, fmt.Errorf("failed to read stats: %w", err)
	}
	return stats, nil
}

func ReadTopStats(ctx context.Context, db *sql.DB, uc UserCacheApi, count int) ([]Stats, error) {
	trace := ctx.Value(TraceKey)

	stats, err := GetTopStats(ctx, db, count)
	if err != nil {
		return nil, fmt.Errorf("failed to read top stats: %w", err)
	}

	eg, egCtx := errgroup.WithContext(ctx)

	for i := range stats {
		stat := &stats[i] // we need to modify each stat when we fetch the player
		name := GetBotName(stat.Player.ID)
		if name != "" {
			stat.Player.Name = name
			continue
		}
		eg.Go(func() error {
			if stat.Player.Name, err = uc.GetUsername(egCtx, stat.Player.ID); err != nil {
				return err
			}
			return nil
		})
	}

	if err := eg.Wait(); err != nil {
		slog.Error("error occurred in fetch username tasks", "trace", trace, "err", err)
		return nil, err
	}
	return stats, nil
}
