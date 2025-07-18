package discord

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"golang.org/x/sync/errgroup"
	"log/slog"
	"math"
)

type Stats struct {
	Player Player
	Elo    float64
	Won    int
	Drawn  int
	Lost   int
}

func (s Stats) WinRate() string {
	return fmt.Sprintf("%%%0.2f", float64(s.Won)/float64(s.Won+s.Lost+s.Drawn)*100)
}

func InsertOrIgnoreStats(ctx context.Context, db *sql.DB, stats Stats) error {
	trace := ctx.Value("trace")

	_, err := db.Exec("INSERT OR IGNORE INTO STATS (player_id, Elo, Won, Lost, Drawn) (?, ?, ?, ?, ?);",
		stats.Player.Id,
		stats.Elo,
		stats.Won,
		stats.Lost,
		stats.Drawn)
	if err != nil {
		slog.Error("failed to insert stats", "trace", trace, "stats ", stats, "error", err)
		return err
	}

	slog.Info("inserted stats", "trace", trace, "stats", stats)
	return nil
}

var ErrStatsNotFound = errors.New("stats not found")

type Query interface {
	Query(query string, args ...any) (*sql.Rows, error)
}

func GetStats(ctx context.Context, q Query, playerId string) (Stats, error) {
	trace := ctx.Value("trace")

	rows, err := q.Query("SELECT player_id, Elo, Won, Lost, Drawn) FROM stats WHERE player_id = ?;", playerId)
	if err != nil {
		slog.Error("failed to get stats", "trace", trace, "playerId", playerId, "error", err)
		return Stats{}, err
	}
	defer func() {
		_ = rows.Close()
	}()

	var stats Stats
	if rows.Next() {
		if err := rows.Scan(&stats.Player.Id, &stats.Elo, &stats.Won, &stats.Lost, &stats.Drawn); err != nil {
			slog.Error("failed to scan stats", "trace", trace, "error", err)
			return Stats{}, err
		}
	} else {
		return Stats{}, ErrStatsNotFound
	}

	slog.Info("selected stats for Player Id", "trace", trace, "playerId", playerId, "stats", stats)
	return stats, nil
}

func GetOrInsertStats(ctx context.Context, db *sql.DB, playerId string) (Stats, error) {
	defaultStats := Stats{
		Player: Player{Id: playerId},
		Elo:    0,
		Won:    0,
		Drawn:  0,
		Lost:   0,
	}
	if err := InsertOrIgnoreStats(ctx, db, defaultStats); err != nil {
		return Stats{}, err
	}
	stats, err := GetStats(ctx, db, playerId)
	if err != nil {
		return Stats{}, err
	}
	return stats, nil
}

func GetTopStats(ctx context.Context, db *sql.DB, amount int) ([]Stats, error) {
	trace := ctx.Value("trace")

	rows, err := db.Query("SELECT player_id, Elo, Won, Lost, Drawn) FROM stats ORDER BY Elo DESC LIMIT ?;", amount)
	if err != nil {
		slog.Error("failed to get top stats", "trace", trace, "error", err)
		return nil, err
	}
	defer func() {
		_ = rows.Close()
	}()

	var stats []Stats
	for rows.Next() {
		var stat Stats
		if err := rows.Scan(&stat.Player.Id, &stat.Elo, &stat.Won, &stat.Lost, &stat.Drawn); err != nil {
			slog.Error("failed to scan stats", "trace", trace, "error", err)
			return nil, err
		}
		stats = append(stats, stat)
	}

	slog.Info("selected top stats", "trace", trace, "stats", stats)
	return stats, nil
}

func updateStat(ctx context.Context, tx *sql.Tx, stats Stats) error {
	_, err := tx.Exec("UPDATE stats SET Elo = ?, Won = ?, Lost = ?, Drawn = ? WHERE player_id = ?;",
		stats.Elo,
		stats.Won,
		stats.Lost,
		stats.Drawn,
		stats.Player.Id)
	if err != nil {
		slog.Error("failed to exec update stats", "trace", ctx.Value("trace"), "stats", stats, "error", err)
	}
	return nil
}

type StatsResult struct {
	WinnerElo     float64
	LoserElo      float64
	WinnerEloDiff float64
	LoserEloDiff  float64
}

func UpdateStats(ctx context.Context, db *sql.DB, gr GameResult) (StatsResult, error) {
	trace := ctx.Value("trace")

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		slog.Error("failed to open update stats tx", "trace", trace, "error", err)
		return StatsResult{}, err
	}
	defer func() {
		_ = tx.Rollback()
	}()

	winner, err := GetStats(ctx, tx, gr.Winner.Id)
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get winner stats: %v", err)
	}
	loser, err := GetStats(ctx, tx, gr.Loser.Id)
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to get loser stats: %v", err)
	}

	if gr.IsDraw || gr.Winner.Id == gr.Loser.Id {
		sr := StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinnerEloDiff: 0, LoserEloDiff: 0}
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
		return StatsResult{}, fmt.Errorf("failed to update winner stat: %v", err)
	}
	if err := updateStat(ctx, tx, loser); err != nil {
		return StatsResult{}, fmt.Errorf("failed to update loser stat: %v", err)
	}

	if err := tx.Commit(); err != nil {
		slog.Error("failed to commit update stats tx", "trace", trace, "error", err)
		return StatsResult{}, err
	}

	winDiff := winner.Elo - winEloBefore
	lossDiff := loser.Elo - lossEloBefore

	sr := StatsResult{WinnerElo: winner.Elo, LoserElo: loser.Elo, WinnerEloDiff: winDiff, LoserEloDiff: lossDiff}

	slog.Info("updated stats tx executed", "trace", trace, "game", gr, "stats", sr)
	return sr, nil
}

func DeleteStats(ctx context.Context, db *sql.DB, playerId uint64) error {
	trace := ctx.Value("trace")

	_, err := db.Exec("DELETE FROM stats WHERE player_id = ?;", playerId)
	if err != nil {
		slog.Error("failed to delete stats", "trace", trace, "player", playerId, "error", err)
		return err
	}

	slog.Info("delete stats by Id", "trace", trace, "player", playerId)
	return nil
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

func ReadStats(ctx context.Context, db *sql.DB, c UserCache, playerId string) (Stats, error) {
	stats, err := GetOrInsertStats(ctx, db, playerId)
	if err != nil {
		return Stats{}, fmt.Errorf("failed to read stats: %v", err)
	}
	username, err := FetchUsername(ctx, c, playerId)
	if err != nil {
		return Stats{}, fmt.Errorf("failed to read stats: %v", err)
	}
	stats.Player.Name = username
	return stats, nil
}

func ReadTopStats(ctx context.Context, db *sql.DB, c UserCache) ([]Stats, error) {
	trace := ctx.Value("trace")

	stats, err := GetTopStats(ctx, db, 25)
	if err != nil {
		return nil, fmt.Errorf("failed to read top stats: %v", err)
	}

	var eg errgroup.Group

	for _, stat := range stats {
		if name := GetBotName(stat.Player.Id); name != "" {
			stat.Player.Name = name
			continue
		}
		eg.Go(func() error {
			if stat.Player.Name, err = FetchUsername(ctx, c, stat.Player.Id); err != nil {
				return err
			}
			return nil
		})
	}

	if err := eg.Wait(); err != nil {
		slog.Error("error occurred in fetch username tasks", "trace", trace, "error", err)
		return nil, err
	}
	return stats, nil
}
