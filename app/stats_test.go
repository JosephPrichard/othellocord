package app

import (
	"context"
	"fmt"
	"math"
	"testing"

	"github.com/bwmarrin/discordgo"
	"github.com/golang/mock/gomock"
	"github.com/jmoiron/sqlx"

	"github.com/stretchr/testify/assert"
	_ "modernc.org/sqlite"
)

func setupStatsTest(t *testing.T) (*sqlx.DB, func()) {
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
			Elo:      1250,
			Won:      5,
			Lost:     2,
			Drawn:    0,
		},
	}

	for _, row := range rows {
		if _, err := GetStatsDefault(ctx, db, row); err != nil {
			t.Fatal("failed to insert stats:", err)
		}
	}

	return db, cleanup
}

func TestReadStats(t *testing.T) {
	db, cleanup := setupStatsTest(t)
	defer cleanup()

	tests := []struct {
		playerID   string
		expStats   Stats
		setupMocks func(ctrl *gomock.Controller) UserFetcher
	}{
		{
			playerID: "id1",
			expStats: Stats{Player: Player{ID: "id1", Name: "Player1"}, Elo: 1750, Won: 3, Lost: 2, Drawn: 1},
			setupMocks: func(ctrl *gomock.Controller) UserFetcher {
				fetcher := NewMockUserFetcher(ctrl)
				fetcher.EXPECT().
					User(gomock.Eq("id1"), gomock.Any()).
					Return(&discordgo.User{ID: "id1", Username: "Player1"}, nil)
				return fetcher
			},
		},
		{
			playerID: "id4",
			expStats: Stats{Player: Player{ID: "id4", Name: "Player4"}, Elo: 1500, Won: 0, Lost: 0, Drawn: 0},
			setupMocks: func(ctrl *gomock.Controller) UserFetcher {
				fetcher := NewMockUserFetcher(ctrl)
				fetcher.EXPECT().
					User(gomock.Eq("id4"), gomock.Any()).
					Return(&discordgo.User{ID: "id4", Username: "Player4"}, nil)
				return fetcher
			},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-next-stats")

			ctrl := gomock.NewController(t)
			defer ctrl.Finish()

			fetcher := test.setupMocks(ctrl)

			userCache := MakeUserCache(fetcher)
			stats, err := ReadStats(ctx, db, &userCache, test.playerID)
			if err != nil {
				t.Fatalf("failed to next stats: %v", err)
			}
			assert.Equal(t, test.expStats, stats)
		})
	}
}

func TestGetTopStats(t *testing.T) {
	db, cleanup := setupStatsTest(t)
	defer cleanup()

	tests := []struct {
		playerID   string
		expStats   []Stats
		setupMocks func(ctrl *gomock.Controller) UserFetcher
	}{
		{
			playerID: "1",
			expStats: []Stats{
				{Player: Player{ID: "id1", Name: "Player1"}, Elo: 1750, Won: 3, Lost: 2, Drawn: 1},
				{Player: Player{ID: "id2", Name: "Player2"}, Elo: 1600, Won: 2, Lost: 4, Drawn: 1},
				{Player: MakeBotPlayer(3), Elo: 1550, Won: 5, Lost: 2, Drawn: 0},
				{Player: Player{ID: "id6", Name: "Player6"}, Elo: 1500, Won: 2, Lost: 4, Drawn: 1},
				{Player: Player{ID: "id7", Name: "Player7"}, Elo: 1250, Won: 5, Lost: 2, Drawn: 0},
			},
			setupMocks: func(ctrl *gomock.Controller) UserFetcher {
				fetcher := NewMockUserFetcher(ctrl)
				for _, mock := range []struct {
					id       string
					username string
				}{
					{id: "id1", username: "Player1"},
					{id: "id2", username: "Player2"},
					{id: "id6", username: "Player6"},
					{id: "id7", username: "Player7"},
				} {
					fetcher.EXPECT().
						User(gomock.Eq(mock.id), gomock.Any()).
						Return(&discordgo.User{ID: mock.id, Username: mock.username}, nil)
				}
				return fetcher
			},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-next-top-stats")

			ctrl := gomock.NewController(t)
			defer ctrl.Finish()

			fetcher := test.setupMocks(ctrl)

			userCache := MakeUserCache(fetcher)
			stats, err := ReadTopStats(ctx, db, &userCache, 20)
			if err != nil {
				t.Fatalf("failed to next stats: %v", err)
			}

			assert.Equal(t, test.expStats, stats)
		})
	}
}

func TestUpdateStats(t *testing.T) {
	db, cleanup := setupStatsTest(t)
	defer cleanup()

	tests := []struct {
		gameResult     GameResult
		expStatsResult StatsResult
		expWinStats    StatsRow
		expLoserStats  StatsRow
	}{
		{
			gameResult:     GameResult{Winner: Player{ID: "id1"}, Loser: Player{ID: "id1"}, IsDraw: false},
			expStatsResult: StatsResult{WinnerElo: 1750, LoserElo: 1750, WinDiff: 0, LoseDiff: 0},
			expWinStats:    StatsRow{PlayerID: "id1", Elo: 1750, Won: 3, Drawn: 1, Lost: 2},
			expLoserStats:  StatsRow{PlayerID: "id1", Elo: 1750, Won: 3, Drawn: 1, Lost: 2},
		},
		{
			gameResult:     GameResult{Winner: Player{ID: "id6"}, Loser: Player{ID: "id7"}, IsDraw: false},
			expStatsResult: StatsResult{WinnerElo: 1506, LoserElo: 1244, WinDiff: 6, LoseDiff: -6},
			expWinStats:    StatsRow{PlayerID: "id6", Elo: 1506, Won: 3, Drawn: 1, Lost: 4},
			expLoserStats:  StatsRow{PlayerID: "id7", Elo: 1244, Won: 5, Drawn: 0, Lost: 3},
		},
	}

	roundElo := func(sr *StatsResult) {
		sr.WinnerElo = math.Round(sr.WinnerElo)
		sr.WinDiff = math.Round(sr.WinDiff)
		sr.LoserElo = math.Round(sr.LoserElo)
		sr.LoseDiff = math.Round(sr.LoseDiff)
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-next-top-stats")

			sr, err := UpdateStats(ctx, db, test.gameResult)
			if err != nil {
				t.Fatalf("failed to update stats: %v", err)
			}

			roundElo(&sr)
			assert.Equal(t, test.expStatsResult, sr)

			ws, err := GetStats(ctx, db, test.gameResult.Winner.ID)
			if err != nil {
				t.Fatalf("failed to get or insert winner stats: %v", err)
			}
			ls, err := GetStats(ctx, db, test.gameResult.Loser.ID)
			if err != nil {
				t.Fatalf("failed to get or insert loser stats: %v", err)
			}
			ws.Elo = math.Round(ws.Elo)
			ls.Elo = math.Round(ls.Elo)

			assert.Equal(t, test.expWinStats, ws)
			assert.Equal(t, test.expLoserStats, ls)
		})
	}
}
