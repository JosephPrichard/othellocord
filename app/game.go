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

func (o *OthelloGame) CreateResult() GameResult {
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

const GameStoreTtl = time.Hour * 24

var ErrGameNotFound = errors.New("game not found")

func GetGame(ctx context.Context, db *sqlx.DB, playerID string) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)

	var row GameRow
	err := db.GetContext(ctx, &row, "SELECT id, board, moves, white_id, black_id, white_name, black_name FROM games WHERE white_id = $1 OR black_id = $1;", playerID)
	if errors.Is(err, sql.ErrNoRows) {
		return OthelloGame{}, ErrGameNotFound
	} else if err != nil {
		return OthelloGame{}, fmt.Errorf("failed to select game by id: %w", err)
	}
	game, err := mapGameRow(row)
	if err != nil {
		return OthelloGame{}, fmt.Errorf("failed to map game row: %w", err)
	}

	slog.Info("selected game", "trace", trace, "game", game.MarshalGGF(), "playerID", playerID)
	return game, nil
}

func CheckGameParticipation(ctx context.Context, tx *sqlx.Tx, player1Id string, player2Id *string) error {
	var count int
	if err := tx.GetContext(ctx, &count, "SELECT COUNT(*) FROM games WHERE white_id = $1 OR black_id = $1 OR white_id = $2 OR black_id = $2;", player1Id, player2Id); err != nil {
		return fmt.Errorf("failed to get games count: %w", err)
	}
	if count > 0 {
		return ErrAlreadyPlaying
	}
	return nil
}

func SetGame(ctx context.Context, ext sqlx.ExtContext, game OthelloGame) error {
	return SetGameTimeWithTime(ctx, ext, game, gameExpireTime())
}

func SetGameTimeWithTime(ctx context.Context, ext sqlx.ExtContext, game OthelloGame, expireTime time.Time) error {
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
		return fmt.Errorf("failed to insert or replace games: %w", err)
	}

	return nil
}

func UpdateGame(ctx context.Context, db *sqlx.DB, game OthelloGame) (StatsResult, error) {
	if len(game.Board.FindCurrentMoves()) == 0 {
		return GameOverTx(ctx, db, game, game.CreateResult())
	} else {
		return StatsResult{}, SetGame(ctx, db, game)
	}
}

func GameOverTx(ctx context.Context, db *sqlx.DB, game OthelloGame, gameResult GameResult) (StatsResult, error) {
	tx, err := db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to open gameover tx: %w", err)
	}
	defer tx.Rollback()

	if _, err := tx.ExecContext(ctx, "DELETE FROM games WHERE white_id = $1 AND black_id = $2;", game.WhitePlayer.ID, game.BlackPlayer.ID); err != nil {
		return StatsResult{}, fmt.Errorf("failed to delete game: %w", err)
	}
	statsResult, err := UpdateStats(ctx, tx, gameResult)
	if err != nil {
		return StatsResult{}, fmt.Errorf("failed to update stats for result=%v: %s", gameResult, err)
	}

	if err := tx.Commit(); err != nil {
		return statsResult, fmt.Errorf("failed to commit gameover tx: %w", err)
	}
	return statsResult, nil
}

func CountGames(db *sqlx.DB) (int, error) {
	var count int
	if err := db.Get(&count, "SELECT COUNT(*) FROM games;"); err != nil {
		return 0, fmt.Errorf("failed to count games: %w", err)
	}
	return count, nil
}

var ErrAlreadyPlaying = errors.New("one or more players are already in a game")

func gameExpireTime() time.Time {
	return time.Now().Add(GameStoreTtl)
}

func CreateGameTx(ctx context.Context, db *sqlx.DB, blackPlayer Player, whitePlayer Player) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)

	game := OthelloGame{ID: uuid.NewString(), WhitePlayer: whitePlayer, BlackPlayer: blackPlayer, Board: MakeInitialBoard()}
	var player2Id *string
	if whitePlayer.IsHuman() {
		player2Id = &whitePlayer.ID
	}

	tx, err := db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return game, fmt.Errorf("failed to open create game tx: %w", err)
	}
	defer tx.Rollback()

	if err := CheckGameParticipation(ctx, tx, blackPlayer.ID, player2Id); err != nil {
		return game, fmt.Errorf("failed to check game participation: %w", err)
	}
	if err := SetGame(ctx, tx, game); err != nil {
		return game, fmt.Errorf("failed to set game: %+v: %w", game, err)
	}

	if err := tx.Commit(); err != nil {
		return game, fmt.Errorf("failed to commit create game tx: %w", err)
	}

	slog.Info("created game", "trace", trace, "game", game.MarshalGGF())
	return game, nil
}

func CreateBotGameTx(ctx context.Context, db *sqlx.DB, blackPlayer Player, level uint64) (OthelloGame, error) {
	return CreateGameTx(ctx, db, blackPlayer, MakeBotPlayer(level))
}

var ErrTurn = errors.New("not players turn")
var ErrInvalidMove = errors.New("invalid move")
var ErrIsAgainstBot = errors.New("game is against bot, must make player's and bot's move as a single transaction")

type MoveAgainstHuman struct {
	PlayerID string
	Tile     Tile
}

func MakeMoveAgainstHuman(ctx context.Context, db *sqlx.DB, move MoveAgainstHuman) (OthelloGame, StatsResult, error) {
	trace := ctx.Value(TraceKey)

	game, err := GetGame(ctx, db, move.PlayerID)
	if err != nil {
		return OthelloGame{}, StatsResult{}, fmt.Errorf("failed to get game in move: %w", err)
	}

	if game.CurrentPlayer().ID != move.PlayerID {
		return game, StatsResult{}, ErrTurn
	}
	if !slices.Contains(game.Board.FindCurrentMoves(), move.Tile) {
		return game, StatsResult{}, ErrInvalidMove
	}

	game.MakeMove(move.Tile)

	if game.CurrentPlayer().IsBot() {
		slog.Info("player made move against bot", "trace", trace, "game", game.MarshalGGF(), "move", move, "playerID", move.PlayerID)
		return game, StatsResult{}, ErrIsAgainstBot
	}

	statsResult, err := UpdateGame(ctx, db, game)
	if err != nil {
		return game, StatsResult{}, fmt.Errorf("failed to update game in move: %w", err)
	}

	slog.Info("player made move", "trace", trace, "game", game.MarshalGGF(), "move", move, "playerID", move.PlayerID)
	return game, statsResult, nil
}

func ExpireGamesCron(db *sqlx.DB) {
	trace := "expire-games-task"
	ctx := context.WithValue(context.Background(), TraceKey, trace)

	ticker := time.NewTicker(time.Minute * 1)
	defer ticker.Stop()

	for range ticker.C {
		slog.Info("executing expire games task", "trace", trace)
		if err := ExpireGames(ctx, db); err != nil {
			slog.Error("failed to expire games", "trace", trace, "err", err)
		}
	}
}

func ExpireGames(ctx context.Context, db *sqlx.DB) error {
	trace := ctx.Value(TraceKey)
	t := time.Now()

	rows, err := db.QueryxContext(ctx, "SELECT id, board, moves, white_id, black_id, white_name, black_name FROM games WHERE expire_time < $1;", t)
	if err != nil {
		return fmt.Errorf("failed to select expired games: %w", err)
	}
	defer rows.Close()

	var games []OthelloGame
	for rows.Next() {
		var row GameRow
		if err := rows.StructScan(&row); err != nil {
			return fmt.Errorf("failed to scan game: %w", err)
		}
		game, err := mapGameRow(row)
		if err != nil {
			return fmt.Errorf("failed to map game row: %w", err)
		}
		games = append(games, game)
	}

	slog.Info("expiring games", "trace", trace, "games", games)

	for _, game := range games {
		sr, err := GameOverTx(ctx, db, game, GameResult{Winner: game.OtherPlayer(), Loser: game.CurrentPlayer(), IsDraw: false})
		if err != nil {
			return fmt.Errorf("failed to update stats: %v for expired games: %w", sr, err)
		}
	}

	return nil
}
