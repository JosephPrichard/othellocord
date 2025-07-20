package discord

import (
	"log/slog"
	"othellocord/app/othello"
)

const (
	GetMovesRequest = iota
	GetMoveRequest
)

const MaxQueueSize = 32
const Workers = 4

type AgentRequest struct {
	board    othello.Board
	depth    int
	t        int
	respChan chan []othello.Move
}

func ListenAgentRequests(w int, agentChan chan AgentRequest) {
	defer func() {
		if r := recover(); r != nil {
			slog.Error("panic in agent worker, restarting", "worker", w)
			go ListenAgentRequests(w, agentChan)
		}
	}()
	for {
		request := <-agentChan
		slog.Info("received an agent request on worker", "worker", w, "request", request)

		request.respChan <- []othello.Move{}
		close(request.respChan)
	}
}

type AgentQueue struct {
	agentChan chan AgentRequest
}

func NewAgentQueue() AgentQueue {
	agentChan := make(chan AgentRequest, MaxQueueSize)
	for w := range Workers {
		go ListenAgentRequests(w, agentChan)
	}
	return AgentQueue{
		agentChan: agentChan,
	}
}

func (q *AgentQueue) Push(request AgentRequest) bool {
	if len(q.agentChan) >= MaxQueueSize {
		return false
	}
	q.agentChan <- request
	return true
}
