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

const AqSize = 16
const AgentCount = 4

type AgentRequest struct {
	ID       string
	Board    othello.Board
	Depth    int
	T        int
	RespChan chan []othello.Move
}

func ListenAgentRequests(w int, agentChan chan AgentRequest) {
	agent := othello.NewOthelloAgent()
	for {
		request := <-agentChan
		slog.Info("received an agent request on worker", "worker", w, "request", request)

		var moves []othello.Move
		switch request.T {
		case GetMovesRequest:
			moves = agent.FindRankedMoves(request.Board, request.Depth)
		case GetMoveRequest:
			if move, ok := agent.FindBestMove(request.Board, request.Depth); ok {
				moves = append(moves, move)
			}
		default:
			slog.Warn("invalid request type", "worker", w, "request", request)
		}

		request.RespChan <- moves
		close(request.RespChan)
	}
}

type AgentQueue struct {
	agentChan chan AgentRequest
}

func NewAgentQueue() AgentQueue {
	agentChan := make(chan AgentRequest, AqSize)
	for w := range AgentCount {
		go ListenAgentRequests(w, agentChan)
	}
	return AgentQueue{agentChan: agentChan}
}

func (q *AgentQueue) PushChecked(request AgentRequest) bool {
	if len(q.agentChan) >= AqSize {
		return false
	}
	q.Push(request)
	return true
}

func (q *AgentQueue) Push(request AgentRequest) {
	request.ID = uuid.NewString()
	slog.Info("dispatched an agent request", "request", request)
	q.agentChan <- request
}
