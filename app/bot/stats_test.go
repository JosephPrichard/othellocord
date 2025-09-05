package bot

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"math"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	_ "modernc.org/sqlite"
)

const TestDb = "./othellocord-temp.db"

func createTestDB() (*sql.DB, func()) {
	db, err := sql.Open("sqlite", TestDb)
	if err != nil {
		log.Fatal(err)
	}
	closer := func() {
		if err := db.Close(); err != nil {
			log.Fatal(err)
		}
		if err := os.Remove(TestDb); err != nil {
			log.Fatal(err)
		}
	}
	if _, err := db.Exec(CreateSchema); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}
	return db, closer
}

func seedStats(t *testing.T, db *sql.DB) {
	ctx := context.WithValue(context.Background(), TraceKey, "seed-insert-stats")

	stats := []Stats{
		{
			Player: Player{ID: "id1"},
			Elo:    1750,
			Won:    3,
			Lost:   2,
			Drawn:  1,
		},
		{
			Player: Player{ID: "id2"},
			Elo:    1600,
			Won:    2,
			Lost:   4,
			Drawn:  1,
		},
		{
			Player: Player{ID: "3"},
			Elo:    1550,
			Won:    5,
			Lost:   2,
			Drawn:  0,
		},
		{
			Player: Player{ID: "id6"},
			Elo:    1500,
			Won:    2,
			Lost:   4,
			Drawn:  1,
		},
		{
			Player: Player{ID: "id7"},
			Elo:    1500,
			Won:    5,
			Lost:   2,
			Drawn:  0,
		},
	}

	for _, stat := range stats {
		if _, err := GetOrInsertStats(ctx, db, "1", stat); err != nil {
			t.Fatal("failed to insert stats:", err)
		}
	}
}

func TestReadStats(t *testing.T) {
	db, cleanup := createTestDB()
	defer cleanup()
	seedStats(t, db)

	type Test struct {
		playerId string
		expStats Stats
	}
	tests := []Test{
		{
			playerId: "id1",
			expStats: Stats{Player: Player{ID: "id1", Name: "Player1"}, Elo: 1750, Won: 3, Lost: 2, Drawn: 1},
		},
		{
			playerId: "id4",
			expStats: Stats{Player: Player{ID: "id4", Name: "Player4"}, Elo: 1500, Won: 0, Lost: 0, Drawn: 0},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-read-stats")

			uc := NewUserCache(&MockUserFetcher{})
			stats, err := ReadStats(ctx, db, &uc, test.playerId)
			if err != nil {
				t.Fatalf("failed to read stats: %v", err)
			}
			assert.Equal(t, test.expStats, stats)
		})
	}
}

func TestGetTopStats(t *testing.T) {
	db, cleanup := createTestDB()
	defer cleanup()
	seedStats(t, db)

	type Test struct {
		playerId string
		expStats []Stats
	}
	tests := []Test{
		{
			playerId: "1",
			expStats: []Stats{
				{Player: Player{ID: "id1", Name: "Player1"}, Elo: 1750, Won: 3, Lost: 2, Drawn: 1},
				{Player: Player{ID: "id2", Name: "Player2"}, Elo: 1600, Won: 2, Lost: 4, Drawn: 1},
				{Player: Player{ID: "3", Name: GetBotName("3")}, Elo: 1550, Won: 5, Lost: 2, Drawn: 0},
				{Player: Player{ID: "id6", Name: "Player6"}, Elo: 1500, Won: 2, Lost: 4, Drawn: 1},
				{Player: Player{ID: "id7", Name: "Player7"}, Elo: 1500, Won: 5, Lost: 2, Drawn: 0},
			},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-read-top-stats")

			uc := NewUserCache(&MockUserFetcher{})
			stats, err := ReadTopStats(ctx, db, &uc, 20)
			if err != nil {
				t.Fatalf("failed to read stats: %v", err)
			}

			assert.Equal(t, test.expStats, stats)
		})
	}
}

func TestUpdateStats(t *testing.T) {
	db, cleanup := createTestDB()
	defer cleanup()
	seedStats(t, db)

	type Test struct {
		gr            GameResult
		expSr         StatsResult
		expWinStats   Stats
		expLoserStats Stats
	}
	tests := []Test{
		{
			gr:            GameResult{Winner: Player{ID: "id1"}, Loser: Player{ID: "id1"}, IsDraw: false},
			expSr:         StatsResult{WinnerElo: 1750, LoserElo: 1750, WinDiff: 0, LoseDiff: 0},
			expWinStats:   Stats{Player: Player{ID: "id1"}, Elo: 1750, Won: 3, Drawn: 1, Lost: 2},
			expLoserStats: Stats{Player: Player{ID: "id1"}, Elo: 1750, Won: 3, Drawn: 1, Lost: 2},
		},
		{
			gr:            GameResult{Winner: Player{ID: "id6"}, Loser: Player{ID: "id7"}, IsDraw: false},
			expSr:         StatsResult{WinnerElo: 1515, LoserElo: 1486, WinDiff: 15, LoseDiff: -14},
			expWinStats:   Stats{Player: Player{ID: "id6"}, Elo: 1515, Won: 3, Drawn: 1, Lost: 4},
			expLoserStats: Stats{Player: Player{ID: "id7"}, Elo: 1486, Won: 5, Drawn: 0, Lost: 3},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-read-top-stats")

			sr, err := UpdateStats(ctx, db, test.gr)
			if err != nil {
				t.Fatalf("failed to update stats: %v", err)
			}

			sr.LoserElo = math.Round(sr.LoserElo)
			sr.LoseDiff = math.Round(sr.LoseDiff)
			assert.Equal(t, test.expSr, sr)

			winnerStats, err := GetOrInsertStats(ctx, db, test.gr.Winner.ID, Stats{})
			if err != nil {
				t.Fatalf("failed to insert winner stats: %v", err)
			}
			loserStats, err := GetOrInsertStats(ctx, db, test.gr.Loser.ID, Stats{})
			if err != nil {
				t.Fatalf("failed to insert loser stats: %v", err)
			}
			winnerStats.Elo = math.Round(winnerStats.Elo)
			loserStats.Elo = math.Round(loserStats.Elo)

			assert.Equal(t, test.expWinStats, winnerStats)
			assert.Equal(t, test.expLoserStats, loserStats)
		})
	}
}
