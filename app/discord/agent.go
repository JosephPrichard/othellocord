package discord

import (
	"log/slog"
	"othellocord/app/othello"
)

const (
	GetMoves = iota
	GetMove
)

const Workers = 4

type AgentRequest struct {
	board    othello.Board
	depth    int
	t        int
	respChan []othello.Move
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
	}
}

func SpawnAgents() chan AgentRequest {
	agentChan := make(chan AgentRequest)
	for w := range Workers {
		go ListenAgentRequests(w, agentChan)
	}
	return agentChan
}
