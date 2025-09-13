package app

import (
	"context"
	"database/sql"
	"fmt"
	"math"
	"testing"

	"github.com/stretchr/testify/assert"
	_ "modernc.org/sqlite"
)

func setupStatsTest(t *testing.T) (*sql.DB, func()) {
	db, cleanup := createTestDB()

	ctx := context.WithValue(context.Background(), TraceKey, "seed-insert-stats")

	rows := []StatsRow{
		{
			PlayerID: "id1",
			Elo:      1750,
			Won:      3,
			Lost:     2,
			Drawn:    1,
		},
		{
			PlayerID: "id2",
			Elo:      1600,
			Won:      2,
			Lost:     4,
			Drawn:    1,
		},
		{
			PlayerID: "3",
			Elo:      1550,
			Won:      5,
			Lost:     2,
			Drawn:    0,
		},
		{
			PlayerID: "id6",
			Elo:      1500,
			Won:      2,
			Lost:     4,
			Drawn:    1,
		},
		{
			PlayerID: "id7",
			Elo:      1500,
			Won:      5,
			Lost:     2,
			Drawn:    0,
		},
	}

	for _, row := range rows {
		if _, err := GetOrInsertStatsDefault(ctx, db, row); err != nil {
			t.Fatal("failed to insert stats:", err)
		}
	}

	return db, cleanup
}

func TestReadStats(t *testing.T) {
	db, cleanup := setupStatsTest(t)
	defer cleanup()

	type Test struct {
		playerID string
		expStats Stats
	}
	tests := []Test{
		{
			playerID: "id1",
			expStats: Stats{Player: Player{ID: "id1", Name: "Player1"}, Elo: 1750, Won: 3, Lost: 2, Drawn: 1},
		},
		{
			playerID: "id4",
			expStats: Stats{Player: Player{ID: "id4", Name: "Player4"}, Elo: 1500, Won: 0, Lost: 0, Drawn: 0},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-read-stats")

			uc := MakeUserCache(&MockUserFetcher{})
			stats, err := ReadStats(ctx, db, &uc, test.playerID)
			if err != nil {
				t.Fatalf("failed to read stats: %v", err)
			}
			assert.Equal(t, test.expStats, stats)
		})
	}
}

func TestGetTopStats(t *testing.T) {
	db, cleanup := setupStatsTest(t)
	defer cleanup()

	type Test struct {
		playerID string
		expStats []Stats
	}
	tests := []Test{
		{
			playerID: "1",
			expStats: []Stats{
				{Player: Player{ID: "id1", Name: "Player1"}, Elo: 1750, Won: 3, Lost: 2, Drawn: 1},
				{Player: Player{ID: "id2", Name: "Player2"}, Elo: 1600, Won: 2, Lost: 4, Drawn: 1},
				{Player: MakeBotPlayer(3), Elo: 1550, Won: 5, Lost: 2, Drawn: 0},
				{Player: Player{ID: "id6", Name: "Player6"}, Elo: 1500, Won: 2, Lost: 4, Drawn: 1},
				{Player: Player{ID: "id7", Name: "Player7"}, Elo: 1500, Won: 5, Lost: 2, Drawn: 0},
			},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-read-top-stats")

			uc := MakeUserCache(&MockUserFetcher{})
			stats, err := ReadTopStats(ctx, db, &uc, 20)
			if err != nil {
				t.Fatalf("failed to read stats: %v", err)
			}

			assert.Equal(t, test.expStats, stats)
		})
	}
}

func TestUpdateStats(t *testing.T) {
	db, cleanup := setupStatsTest(t)
	defer cleanup()

	type Test struct {
		gr            GameResult
		expSr         StatsResult
		expWinStats   StatsRow
		expLoserStats StatsRow
	}
	tests := []Test{
		{
			gr:            GameResult{Winner: Player{ID: "id1"}, Loser: Player{ID: "id1"}, IsDraw: false},
			expSr:         StatsResult{WinnerElo: 1750, LoserElo: 1750, WinDiff: 0, LoseDiff: 0},
			expWinStats:   StatsRow{PlayerID: "id1", Elo: 1750, Won: 3, Drawn: 1, Lost: 2},
			expLoserStats: StatsRow{PlayerID: "id1", Elo: 1750, Won: 3, Drawn: 1, Lost: 2},
		},
		{
			gr:            GameResult{Winner: Player{ID: "id6"}, Loser: Player{ID: "id7"}, IsDraw: false},
			expSr:         StatsResult{WinnerElo: 1515, LoserElo: 1486, WinDiff: 15, LoseDiff: -14},
			expWinStats:   StatsRow{PlayerID: "id6", Elo: 1515, Won: 3, Drawn: 1, Lost: 4},
			expLoserStats: StatsRow{PlayerID: "id7", Elo: 1486, Won: 5, Drawn: 0, Lost: 3},
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

			winnerStats, err := GetOrInsertStats(ctx, db, test.gr.Winner.ID)
			if err != nil {
				t.Fatalf("failed to insert winner stats: %v", err)
			}
			loserStats, err := GetOrInsertStats(ctx, db, test.gr.Loser.ID)
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
