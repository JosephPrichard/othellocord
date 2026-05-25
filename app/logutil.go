package app

import (
	"context"
	"log/slog"
	"os"
)

type TraceHandler struct {
	slog.Handler
}

func (h *TraceHandler) Handle(ctx context.Context, r slog.Record) error {
	if v := ctx.Value(TraceKey); v != nil {
		r.Add("trace", v)
	}
	return h.Handler.Handle(ctx, r)
}

func InitLogger() {
	base := slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelDebug,
	})
	slog.SetDefault(slog.New(&TraceHandler{base}))
}
