package app

import (
	"context"
	"github.com/bwmarrin/discordgo"
	"github.com/google/go-cmp/cmp"
	"github.com/google/go-cmp/cmp/cmpopts"
	"github.com/jmoiron/sqlx"
	"go.uber.org/mock/gomock"
	"testing"
	"time"
)

var cmpOptsWebhookEdit = []cmp.Option{
	cmpopts.IgnoreFields(discordgo.WebhookEdit{}, "Files"),
	cmpopts.IgnoreFields(discordgo.MessageEmbed{}, "Color"),
}
var cmpOptsInteractionResponse = []cmp.Option{
	cmpopts.IgnoreFields(discordgo.InteractionResponseData{}, "Files"),
	cmpopts.IgnoreFields(discordgo.Button{}, "CustomID", "Style"),
	cmpopts.IgnoreFields(discordgo.MessageEmbed{}, "Color"),
}

func setupGameHandlersTest(t *testing.T) (*sqlx.DB, func()) {
	db, cleanup := createTestDB()
	ctx := t.Context()

	games := []OthelloGame{
		{
			ID:          "1",
			Board:       MakeInitialBoard(),
			BlackPlayer: Player{ID: "id1", Name: "Player1"},
			WhitePlayer: Player{ID: "id2", Name: "Player2"},
		},
		{
			ID:          "2",
			Board:       MakeInitialBoard(),
			BlackPlayer: Player{ID: "id10", Name: "Player10"},
			WhitePlayer: Player{ID: "id20", Name: "Player20"},
			MoveList:    []Move{{Tile: Tile{Row: 0, Col: 0}}},
		},
		{
			ID:          "3",
			Board:       MakeInitialBoard(),
			BlackPlayer: Player{ID: "id3", Name: "Player3"},
			WhitePlayer: MakeBotPlayer(1),
		},
	}

	for _, game := range games {
		if err := setGameWithTime(ctx, db, game, time.Time{}); err != nil {
			t.Fatal("failed to insert games:", err)
		}
	}

	return db, cleanup
}

func TestHandleAnalyze(t *testing.T) {
	setupMockDiscordAPI := func(t *testing.T, ctrl *gomock.Controller) *MockDiscordAPI {
		discord := NewMockDiscordAPI(ctrl)
		discord.EXPECT().
			InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
				Type: discordgo.InteractionResponseChannelMessageWithSource,
				Data: &discordgo.InteractionResponseData{
					Content: "Analyzing... Wait a second...",
				},
			})).
			Return(nil)
		return discord
	}

	tests := []struct {
		name       string
		level      float64
		userID     string
		setupMocks func(t *testing.T, ctrl *gomock.Controller) (DiscordAPI, NTestShellAPI)
	}{
		{
			name:   "LevelProvided",
			level:  1,
			userID: "id1",
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) (DiscordAPI, NTestShellAPI) {
				discord := setupMockDiscordAPI(t, ctrl)

				discord.EXPECT().
					InteractionResponseEdit(gomock.Any(), mockMatcher(t, &discordgo.WebhookEdit{
						Content: ptr(""),
						Embeds: &[]*discordgo.MessageEmbed{
							{
								Title:       "Game analysis using engine level 1",
								Description: "Black: 2 points\nWhite: 2 points\n",
								Footer: &discordgo.MessageEmbedFooter{
									Text: "Positive heuristics are better for the player to move, and negative heuristics are worse",
								},
								Image: &discordgo.MessageEmbedImage{URL: "attachment://image.png"},
							},
						},
						Attachments: &[]*discordgo.MessageAttachment{},
					}, cmpOptsWebhookEdit...)).
					Return(nil, nil)

				shell := NewMockNTestShellAPI(ctrl)
				shell.EXPECT().
					FindRankedMoves(gomock.Any(), gomock.Any(), gomock.Any()).
					Return(MoveResult{Moves: []RankTile{{H: 1.0, Tile: Tile{Row: 1, Col: 1}}}}, nil)

				return discord, shell
			},
		},
		{
			name:   "GameNotFound",
			level:  1,
			userID: "id-invalid",
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) (DiscordAPI, NTestShellAPI) {
				discord := NewMockDiscordAPI(ctrl)
				discord.EXPECT().
					InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
						Type: discordgo.InteractionResponseChannelMessageWithSource,
						Data: &discordgo.InteractionResponseData{
							Content: "You're not playing a game.",
						},
					})).
					Return(nil)

				return discord, NewMockNTestShellAPI(ctrl)
			},
		},
		{
			name:   "AnalysisTimeout",
			level:  1,
			userID: "id2",
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) (DiscordAPI, NTestShellAPI) {
				discord := setupMockDiscordAPI(t, ctrl)

				discord.EXPECT().
					InteractionResponseEdit(gomock.Any(), mockMatcher(t, &discordgo.WebhookEdit{
						Content: ptr("Timed out while waiting for a response."),
					}, cmpOptsWebhookEdit...)).
					Return(nil, nil)

				shell := NewMockNTestShellAPI(ctrl)
				shell.EXPECT().
					FindRankedMoves(gomock.Any(), gomock.Any(), gomock.Any()).
					Return(MoveResult{}, context.DeadlineExceeded)

				return discord, shell
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			db, cleanup := setupGameHandlersTest(t)
			defer cleanup()

			ctrl := gomock.NewController(t)
			defer ctrl.Finish()

			discord, shell := tt.setupMocks(t, ctrl)

			handler := Handler{
				discord:     discord,
				shell:       shell,
				renderer:    MakeRenderCache(),
				gameService: MakeGameService(db),
			}
			handler.HandleInteractionCreate(t.Context(), &discordgo.InteractionCreate{
				Interaction: &discordgo.Interaction{
					Type: discordgo.InteractionApplicationCommand,
					Data: discordgo.ApplicationCommandInteractionData{
						Name: "analyze",
						Options: []*discordgo.ApplicationCommandInteractionDataOption{
							{
								Name:  "level",
								Value: tt.level,
							},
						},
					},
					Member: &discordgo.Member{User: &discordgo.User{ID: tt.userID}},
				},
			})
		})
	}
}

func TestHandleSimulate(t *testing.T) {
	testWhiteLevel := uint64(1)
	testBlackLevel := uint64(2)

	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	discord := NewMockDiscordAPI(ctrl)

	discord.EXPECT().
		InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
			Type: discordgo.InteractionResponseChannelMessageWithSource,
			Data: &discordgo.InteractionResponseData{
				Components: []discordgo.MessageComponent{
					discordgo.ActionsRow{
						Components: []discordgo.MessageComponent{discordgo.Button{Label: "Stop"}, discordgo.Button{Label: "Pause"}},
					},
				},
				Embeds: []*discordgo.MessageEmbed{
					{
						Title:       "Simulation started!",
						Description: "Black: NTest level 2\n White: NTest level 1",
						Image:       &discordgo.MessageEmbedImage{URL: "attachment://image.png"},
					},
				},
			},
		}, cmpOptsInteractionResponse...)).
		Return(nil)

	shell := NewMockNTestShellAPI(ctrl)

	game := OthelloGame{
		Board:       MakeInitialBoard(),
		WhitePlayer: MakeBotPlayer(testWhiteLevel),
		BlackPlayer: MakeBotPlayer(testBlackLevel),
	}
	calls := 0
	for {
		calls++

		moves := game.Board.FindCurrentMoves()
		if len(moves) == 0 {
			break
		}
		move := moves[0]

		t.Logf("expecting game %d: %s %s\n", calls, move, game)

		shell.EXPECT().
			FindBestMove(gomock.Any(), mockMatcherFmt(t, game), gomock.Any()).
			Return(MoveResult{Move: RankTile{Tile: move}}, nil)

		game.MakeMove(move)
	}

	discord.EXPECT().
		InteractionResponseEdit(gomock.Any(), gomock.Any()).
		Return(nil, nil).
		Times(calls)

	handler := Handler{
		discord:      discord,
		shell:        shell,
		renderer:     MakeRenderCache(),
		simCache:     MakeSimCache(),
		defaultDelay: time.Nanosecond * 1,
	}
	handler.HandleInteractionCreate(t.Context(), &discordgo.InteractionCreate{
		Interaction: &discordgo.Interaction{
			Type: discordgo.InteractionApplicationCommand,
			Data: discordgo.ApplicationCommandInteractionData{
				Name: "simulate",
				Options: []*discordgo.ApplicationCommandInteractionDataOption{
					{
						Name:  "white-level",
						Value: float64(testWhiteLevel),
					},
					{
						Name:  "black-level",
						Value: float64(testBlackLevel),
					},
				},
			},
		},
	})
}

func TestHandleMove(t *testing.T) {
	initialGame := OthelloGame{ID: "1", Board: MakeInitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	firstMove := initialGame.Board.FindCurrentMoves()[0]
	expGame := initialGame
	expGame.MakeMove(firstMove)

	testChannelID := "channel1"

	tests := []struct {
		name       string
		userID     string
		move       string
		setupMocks func(t *testing.T, ctrl *gomock.Controller) DiscordAPI
	}{
		{
			name:   "GameNotFound",
			userID: "id-invalid",
			move:   "a1",
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) DiscordAPI {
				discord := NewMockDiscordAPI(ctrl)

				discord.EXPECT().
					InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
						Type: discordgo.InteractionResponseChannelMessageWithSource,
						Data: &discordgo.InteractionResponseData{Content: "You're not currently playing a game."},
					})).
					Return(nil)

				return discord
			},
		},
		{
			name:   "NotYourTurn",
			userID: "id2",
			move:   "a1",
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) DiscordAPI {
				discord := NewMockDiscordAPI(ctrl)

				discord.EXPECT().
					InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
						Type: discordgo.InteractionResponseChannelMessageWithSource,
						Data: &discordgo.InteractionResponseData{Content: "It isn't your turn."},
					})).
					Return(nil)

				return discord
			},
		},
		{
			name:   "InvalidMove",
			userID: "id1",
			move:   "b1",
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) DiscordAPI {
				discord := NewMockDiscordAPI(ctrl)

				discord.EXPECT().
					InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
						Type: discordgo.InteractionResponseChannelMessageWithSource,
						Data: &discordgo.InteractionResponseData{Content: "Can't make a move to b1."},
					})).
					Return(nil)

				return discord
			},
		},
		{
			name:   "ValidMove_AgainstHuman",
			userID: "id1",
			move:   "C4",
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) DiscordAPI {
				discord := NewMockDiscordAPI(ctrl)

				discord.EXPECT().
					InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
						Type: discordgo.InteractionResponseChannelMessageWithSource,
						Data: &discordgo.InteractionResponseData{
							Embeds: []*discordgo.MessageEmbed{
								{
									Title:       "Player1 vs Player2",
									Description: "Black: 4 points\nWhite: 1 points\nPlayer1 made move: C4",
									Footer:      &discordgo.MessageEmbedFooter{Text: "White to move"},
									Image:       &discordgo.MessageEmbedImage{URL: "attachment://image.png"},
								},
							},
						},
					}, cmpOptsInteractionResponse...)).
					Return(nil)
				discord.EXPECT().
					ChannelMessageSendComplex(gomock.Eq(testChannelID), mockMatcher(t, &discordgo.MessageSend{
						Content: "<@id2>",
					}))

				return discord
			},
		},
		{
			name:   "ValidMove_AgainstBot",
			userID: "id3",
			move:   firstMove.String(),
			setupMocks: func(t *testing.T, ctrl *gomock.Controller) DiscordAPI {
				discord := NewMockDiscordAPI(ctrl)

				discord.EXPECT().
					InteractionRespond(gomock.Any(), mockMatcher(t, &discordgo.InteractionResponse{
						Type: discordgo.InteractionResponseChannelMessageWithSource,
						Data: &discordgo.InteractionResponseData{
							Embeds: []*discordgo.MessageEmbed{
								{
									Title:       "Player3 vs NTest level 1",
									Description: "Black: 4 points\nWhite: 1 points\nNTest level 1 to move",
									Footer:      &discordgo.MessageEmbedFooter{Text: "White to move"},
									Image:       &discordgo.MessageEmbedImage{URL: "attachment://image.png"},
								},
							},
						},
					}, cmpOptsInteractionResponse...)).
					Return(nil)

				return discord
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			db, cleanup := setupGameHandlersTest(t)
			defer cleanup()

			ctrl := gomock.NewController(t)
			defer ctrl.Finish()

			discord := tt.setupMocks(t, gomock.NewController(t))

			handler := Handler{
				discord:      discord,
				renderer:     MakeRenderCache(),
				gameService:  MakeGameService(db),
				simCache:     MakeSimCache(),
				defaultDelay: time.Nanosecond * 1,
			}
			handler.HandleInteractionCreate(t.Context(), &discordgo.InteractionCreate{
				Interaction: &discordgo.Interaction{
					Type: discordgo.InteractionApplicationCommand,
					Data: discordgo.ApplicationCommandInteractionData{
						Name: "move",
						Options: []*discordgo.ApplicationCommandInteractionDataOption{
							{
								Name:  "move",
								Value: tt.move,
							},
						},
					},
					Member:    &discordgo.Member{User: &discordgo.User{ID: tt.userID}},
					ChannelID: testChannelID,
				},
			})
		})
	}
}
