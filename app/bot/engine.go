package bot

import (
	"github.com/google/uuid"
	"log/slog"
	"othellocord/app/othello"
)

const (
	GetMovesRequest = iota
	GetMoveRequest
)

const EqSize = 16
const EngineCount = 4

type EngineRequest struct {
	ID       string
	Board    othello.Board
	Depth    int
	T        int
	RespChan chan []othello.RankTile
}

func ListenEngineRequest(w int, engineChan chan EngineRequest) {
	engine := othello.NewEngine()
	for request := range engineChan {
		slog.Info("received an engine request on worker", "worker", w, "request", request)

		var moves []othello.RankTile
		switch request.T {
		case GetMovesRequest:
			moves = engine.FindRankedMoves(request.Board, request.Depth)
		case GetMoveRequest:
			if move, ok := engine.FindBestMove(request.Board, request.Depth); ok {
				moves = append(moves, move)
			}
		default:
			slog.Warn("invalid request type", "worker", w, "request", request)
		}

		request.RespChan <- moves
		close(request.RespChan)
	}
}

type EngineQ struct {
	engineChan chan EngineRequest
}

func NewEngineQ() EngineQ {
	engineChan := make(chan EngineRequest, EqSize)
	for w := range EngineCount {
		go ListenEngineRequest(w, engineChan)
	}
	return EngineQ{engineChan: engineChan}
}

func (q *EngineQ) PushSafe(request EngineRequest) bool {
	if len(q.engineChan) >= EqSize {
		return false
	}
	q.Push(request)
	return true
}

func (q *EngineQ) Push(request EngineRequest) {
	request.ID = uuid.NewString()
	slog.Info("dispatched an engine request", "request", request)
	q.engineChan <- request
}
