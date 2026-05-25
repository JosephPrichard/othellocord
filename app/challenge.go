package app

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/jellydator/ttlcache/v3"
)

var ChallengeTTl = time.Second * 60

type Challenge struct {
	Challenged Player
	Challenger Player
}

func (c Challenge) Key() string {
	return fmt.Sprintf("%s,%s", c.Challenged.ID, c.Challenger.ID)
}

type ChallengeCache struct {
	store *ttlcache.Cache[string, chan struct{}]
}

func MakeChallengeCache() ChallengeCache {
	return ChallengeCache{store: ttlcache.New[string, chan struct{}]()}
}

func (cc ChallengeCache) CreateChallenge(ctx context.Context, challenge Challenge, handleExpire func()) {

	stopChan := make(chan struct{}, 1)

	key := challenge.Key()
	_ = cc.store.Set(key, stopChan, ChallengeTTl)
	slog.InfoContext(ctx, "set challenge into challenge Cache", "key", key, "challenge", challenge)

	go func() {
		defer cc.store.Delete(key)

		timer := time.NewTimer(ChallengeTTl)
		select {
		case <-timer.C:
			slog.InfoContext(ctx, "expired challenge", "key", key, "challenge", challenge)
			handleExpire()
			return
		case <-stopChan:
			slog.InfoContext(ctx, "stopped challenge", "key", key, "challenge", challenge)
			return
		}
	}()
}

func (cc ChallengeCache) AcceptChallenge(ctx context.Context, challenge Challenge) bool {
	key := challenge.Key()

	item := cc.store.Get(key)
	if item == nil {
		return false
	}

	stopChan := item.Value()
	if stopChan != nil {
		stopChan <- struct{}{}
	}

	slog.InfoContext(ctx, "accepted challenge from challenge Cache", "key", key, "challenge", challenge)
	return true
}
