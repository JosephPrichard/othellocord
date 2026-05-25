package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"log/slog"
	"slices"
	"time"
)

type OthelloGame struct {
	ID          string
	Board       OthelloBoard
	WhitePlayer Player
	BlackPlayer Player
	MoveList    []Move
}

func (o OthelloGame) String() string {
	return fmt.Sprintf("OthelloGame{ID=%s, Board=%s, WhitePlayer=%+v, BlackPlayer=%+v, MoveList=%+v}",
		o.ID,
		o.Board.MarshallGGF(),
		o.WhitePlayer,
		o.BlackPlayer,
		o.MoveList)
}

type Move struct {
	Tile
	Pass bool
}

func (move Move) String() string {
	if move.Pass {
		return "PA"
	} else {
		return move.Tile.String()
	}
}

type MoveKind int

const (
	Regular MoveKind = iota
	Pass
)

func (o *OthelloGame) MakeMove(move Tile) MoveKind {
	o.Board.MakeMove(move)
	o.MoveList = append(o.MoveList, Move{Tile: move, Pass: false})

	if len(o.Board.FindCurrentMoves()) == 0 {
		o.Board.IsBlackMove = !o.Board.IsBlackMove
		o.MoveList = append(o.MoveList, Move{Pass: true})
		return Pass
	}
	return Regular
}

func (o *OthelloGame) HasMoves() bool {
	return len(o.Board.FindCurrentMoves()) > 0
}

func (o *OthelloGame) IsOver() bool {
	return !o.HasMoves()
}

func (o *OthelloGame) CurrentPlayer() Player {
	if o.Board.IsBlackMove {
		return o.BlackPlayer
	} else {
		return o.WhitePlayer
	}
}

func (o *OthelloGame) OtherPlayer() Player {
	if o.Board.IsBlackMove {
		return o.WhitePlayer
	} else {
		return o.BlackPlayer
	}
}

func (o *OthelloGame) MakeResult() GameResult {
	diff := o.Board.BlackScore() - o.Board.WhiteScore()
	if diff > 0 {
		return GameResult{Winner: o.BlackPlayer, Loser: o.WhitePlayer, IsDraw: false}
	} else if diff < 0 {
		return GameResult{Winner: o.WhitePlayer, Loser: o.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{Winner: o.BlackPlayer, Loser: o.WhitePlayer, IsDraw: true}
	}
}

func (o *OthelloGame) CreateForfeitResult(forfeitId string) GameResult {
	if o.WhitePlayer.ID == forfeitId {
		return GameResult{Winner: o.BlackPlayer, Loser: o.WhitePlayer, IsDraw: false}
	} else if o.BlackPlayer.ID == forfeitId {
		return GameResult{Winner: o.WhitePlayer, Loser: o.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{IsDraw: true}
	}
}

type GameResult struct {
	Winner Player
	Loser  Player
	IsDraw bool
}

type GameRow struct {
	ID          string `db:"id"`
	BoardStr    string `db:"board"`
	MoveListStr string `db:"moves"`
	WhiteID     string `db:"white_id"`
	BlackID     string `db:"black_id"`
	WhiteName   string `db:"white_name"`
	BlackName   string `db:"black_name"`
}

func mapGameRow(row GameRow) (OthelloGame, error) {
	game := OthelloGame{ID: row.ID, WhitePlayer: MakePlayer(row.WhiteID, row.WhiteName), BlackPlayer: MakePlayer(row.BlackID, row.BlackName)}

	board, err := UnmarshalBoard(row.BoardStr)
	if err != nil {
		return OthelloGame{}, err
	}
	moveList, err := UnmarshalMoveList(row.MoveListStr)
	if err != nil {
		return OthelloGame{}, err
	}

	game.Board = board
	game.MoveList = moveList
	return game, nil
}

type GameService struct {
	database *sqlx.DB
}

func MakeGameService(db *sqlx.DB) *GameService {
	return &GameService{database: db}
}

const GameStoreTtl = time.Hour * 24

var ErrGameNotFound = errors.New("game not found")

func (service *GameService) GetGame(ctx context.Context, playerID string) (OthelloGame, error) {
	var row GameRow
	err := service.database.GetContext(ctx, &row,
		"SELECT id, board, moves, white_id, black_id, white_name, black_name FROM games WHERE white_id = $1 OR black_id = $1;",
		playerID)
	if errors.Is(err, sql.ErrNoRows) {
		slog.InfoContext(ctx, "game not found", "playerID", playerID)
		return OthelloGame{}, ErrGameNotFound
	} else if err != nil {
		return OthelloGame{}, fmt.Errorf("select game by id: %w", err)
	}
	game, err := mapGameRow(row)
	if err != nil {
		return OthelloGame{}, fmt.Errorf("map game row: %w", err)
	}

	slog.InfoContext(ctx, "selected game", "game", game, "playerID", playerID)
	return game, nil
}

func setGame(ctx context.Context, ext sqlx.ExtContext, game OthelloGame) error {
	return setGameWithTime(ctx, ext, game, gameExpireTime())
}

func setGameWithTime(ctx context.Context, ext sqlx.ExtContext, game OthelloGame, expireTime time.Time) error {
	boardStr := game.Board.MarshalString()
	moveListStr := MarshalMoveList(game.MoveList)

	_, err := ext.ExecContext(ctx,
		"INSERT OR REPLACE INTO games (id, board, white_id, black_id, white_name, black_name, moves, expire_time) VALUES ($1, $2, $3, $4, $5, $6, $7, $8);",
		game.ID,
		boardStr,
		game.WhitePlayer.ID,
		game.BlackPlayer.ID,
		game.WhitePlayer.Name,
		game.BlackPlayer.Name,
		moveListStr,
		expireTime,
	)
	if err != nil {
		return fmt.Errorf("insert or replace games: %w", err)
	}

	slog.InfoContext(ctx, "set game", "game", game, "expireTime", expireTime)
	return nil
}

func (service *GameService) UpdateGame(ctx context.Context, game OthelloGame) (StatsResult, error) {
	if len(game.Board.FindCurrentMoves()) == 0 {
		return service.UpdateGameOver(ctx, game, game.MakeResult())
	} else {
		return StatsResult{}, setGame(ctx, service.database, game)
	}
}

func (service *GameService) UpdateGameOver(ctx context.Context, game OthelloGame, gameResult GameResult) (StatsResult, error) {
	tx, err := service.database.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return StatsResult{}, fmt.Errorf("open game over tx: %w", err)
	}
	defer tx.Rollback()

	slog.InfoContext(ctx, "handling game over event", "game", game, "gameResult", gameResult)

	if _, err := tx.ExecContext(ctx, "DELETE FROM games WHERE white_id = $1 AND black_id = $2;", game.WhitePlayer.ID, game.BlackPlayer.ID); err != nil {
		return StatsResult{}, fmt.Errorf("delete game: %w", err)
	}
	statsResult, err := UpdateStats(ctx, tx, gameResult)
	if err != nil {
		return StatsResult{}, fmt.Errorf("update stats for result=%v: %s", gameResult, err)
	}

	if err := tx.Commit(); err != nil {
		return statsResult, fmt.Errorf("commit game over tx: %w", err)
	}
	return statsResult, nil
}

var ErrAlreadyPlaying = errors.New("one or more players are already in a game")

func gameExpireTime() time.Time {
	return time.Now().Add(GameStoreTtl)
}

func (service *GameService) CreateGame(ctx context.Context, blackPlayer Player, whitePlayer Player) (OthelloGame, error) {
	game := OthelloGame{ID: uuid.NewString(), WhitePlayer: whitePlayer, BlackPlayer: blackPlayer, Board: MakeInitialBoard()}

	// assumes that the challenger is black, and is a human
	var player2Id *string
	if whitePlayer.IsHuman() {
		player2Id = &whitePlayer.ID
	}

	tx, err := service.database.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return OthelloGame{}, fmt.Errorf("open create game tx: %w", err)
	}
	defer tx.Rollback()

	var participantGameCount int
	err = tx.GetContext(ctx, &participantGameCount,
		"SELECT COUNT(*) FROM games WHERE white_id = $1 OR black_id = $1 OR white_id = $2 OR black_id = $2;", blackPlayer.ID, player2Id)
	if err != nil {
		return OthelloGame{}, fmt.Errorf("get games count for players %+v and %+v: %w", blackPlayer.ID, player2Id, err)
	}
	if participantGameCount > 0 {
		return OthelloGame{}, ErrAlreadyPlaying
	}

	if err := setGame(ctx, tx, game); err != nil {
		return OthelloGame{}, fmt.Errorf("set game: %+v: %w", game, err)
	}

	if err := tx.Commit(); err != nil {
		return OthelloGame{}, fmt.Errorf("commit create game tx: %w", err)
	}

	slog.InfoContext(ctx, "created game", "game", game.MarshalGGF())
	return game, nil
}

func (service *GameService) CreateBotGame(ctx context.Context, blackPlayer Player, level uint64) (OthelloGame, error) {
	return service.CreateGame(ctx, blackPlayer, MakeBotPlayer(level))
}

var ErrTurn = errors.New("not players turn")
var ErrInvalidMove = errors.New("invalid move")
var ErrIsAgainstBot = errors.New("game is against bot, must make player's and bot's move as a single transaction")

type MoveAgainstHuman struct {
	PlayerID string
	Tile     Tile
}

func (service *GameService) MakeMoveAgainstHuman(ctx context.Context, move MoveAgainstHuman) (OthelloGame, StatsResult, error) {
	game, err := service.GetGame(ctx, move.PlayerID)
	if err != nil {
		return OthelloGame{}, StatsResult{}, fmt.Errorf("get game in move: %w", err)
	}

	if game.CurrentPlayer().ID != move.PlayerID {
		return game, StatsResult{}, ErrTurn
	}
	if !slices.Contains(game.Board.FindCurrentMoves(), move.Tile) {
		return game, StatsResult{}, ErrInvalidMove
	}

	game.MakeMove(move.Tile)

	if game.CurrentPlayer().IsBot() {
		slog.InfoContext(ctx, "player made move against bot", "game", game, "move", move, "playerID", move.PlayerID)
		return game, StatsResult{}, ErrIsAgainstBot
	}

	statsResult, err := service.UpdateGame(ctx, game)
	if err != nil {
		return game, StatsResult{}, fmt.Errorf("update game in move: %w", err)
	}

	slog.InfoContext(ctx, "player made move against human", "game", game, "move", move, "playerID", move.PlayerID)
	return game, statsResult, nil
}

func ExpireGamesCron(db *sqlx.DB) {
	trace := "expire-games-task"
	ctx := context.WithValue(context.Background(), TraceKey, trace)

	ticker := time.NewTicker(time.Minute * 1)
	defer ticker.Stop()

	gameService := &GameService{database: db}

	for range ticker.C {
		slog.InfoContext(ctx, "executing expire games task", "trace", trace)
		if err := gameService.ExpireGames(ctx); err != nil {
			slog.ErrorContext(ctx, "failed to expire games", "err", err)
		}
	}
}

func (service *GameService) ExpireGames(ctx context.Context) error {
	t := time.Now()

	rows, err := service.database.QueryxContext(ctx, "SELECT id, board, moves, white_id, black_id, white_name, black_name FROM games WHERE expire_time < $1;", t)
	if err != nil {
		return fmt.Errorf("select expired games: %w", err)
	}
	defer rows.Close()

	var games []OthelloGame
	for rows.Next() {
		var row GameRow
		if err := rows.StructScan(&row); err != nil {
			return fmt.Errorf("scan game: %w", err)
		}
		game, err := mapGameRow(row)
		if err != nil {
			return fmt.Errorf("map game row: %w", err)
		}
		games = append(games, game)
	}

	slog.InfoContext(ctx, "expiring games", "games", games)

	for _, game := range games {
		sr, err := service.UpdateGameOver(ctx, game, GameResult{Winner: game.OtherPlayer(), Loser: game.CurrentPlayer(), IsDraw: false})
		if err != nil {
			return fmt.Errorf("update stats: %v for expired games: %w", sr, err)
		}
	}

	return nil
}
