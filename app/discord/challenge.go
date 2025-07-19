package discord

import (
	"context"
	"errors"
	"fmt"
	"github.com/coocood/freecache"
	cache2 "github.com/eko/gocache/lib/v4/cache"
	"log/slog"
	"time"
)

type Challenge struct {
	Challenged Player
	Challenger Player
}

type ChallengeCache = cache2.Cache[chan struct{}]

func CreateChallenge(ctx context.Context, c ChallengeCache, challenge Challenge, onExpiry func()) error {
	trace := ctx.Value("trace")

	stopChan := make(chan struct{}, 1)

	key := fmt.Sprintf("%s,%s", challenge.Challenged.Id, challenge.Challenger.Id)
	if err := c.Set(ctx, key, stopChan); err != nil {
		slog.Error("failed to set challenge into Store", "trace", trace, "key", key, "err", err)
		return err
	}

	go func() {
		timer := time.NewTimer(60 * time.Second)
		defer timer.Stop()
		select {
		case <-timer.C:
			onExpiry()
		case <-stopChan:
			return
		}
	}()

	return nil
}

var ChallengeNotFound = fmt.Errorf("challenge not found")

func AcceptChallenge(ctx context.Context, c ChallengeCache, challenge Challenge) error {
	trace := ctx.Value("trace")

	key := fmt.Sprintf("%s,%s", challenge.Challenged.Id, challenge.Challenger.Id)

	stopChan, err := c.Get(ctx, key)
	if errors.Is(err, freecache.ErrNotFound) {
		return ChallengeNotFound
	}
	if err != nil {
		slog.Error("failed to get challenge from Store", "trace", trace, "key", key, "err", err)
		return err
	}
	if stopChan != nil {
		stopChan <- struct{}{}
	}
	if err := c.Delete(ctx, key); err != nil {
		slog.Error("failed to delete challenge in Store", "trace", trace, "key", key, "err", err)
	}
	return nil
}
