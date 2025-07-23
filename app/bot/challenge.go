package bot

import (
	"context"
	"fmt"
	"github.com/jellydator/ttlcache/v3"
	"log/slog"
	"time"
)

type Challenge struct {
	Challenged Player
	Challenger Player
}

func (c Challenge) Key() string {
	return fmt.Sprintf("%s,%s", c.Challenged.ID, c.Challenger.ID)
}

type ChallengeCache struct {
	cache *ttlcache.Cache[string, chan struct{}]
}

func NewChallengeCache() ChallengeCache {
	return ChallengeCache{
		cache: ttlcache.New[string, chan struct{}](),
	}
}

var ChallengeTTl = time.Second * 60

func (c ChallengeCache) CreateChallenge(ctx context.Context, challenge Challenge, onExpire func()) {
	trace := ctx.Value("trace")

	stopChan := make(chan struct{}, 1)

	key := challenge.Key()
	_ = c.cache.Set(key, stopChan, ChallengeTTl)
	slog.Info("set challenge into challenge Cache", "trace", trace, "key", key, "challenge", challenge)

	go func() {
		defer c.cache.Delete(key)

		timer := time.NewTimer(ChallengeTTl)
		select {
		case <-timer.C:
			slog.Info("expired challenge", "trace", trace, "key", key, "challenge", challenge)
			onExpire()
			return
		case <-stopChan:
			slog.Info("stopped challenge", "trace", trace, "key", key, "challenge", challenge)
			return
		}
	}()
}

func (c ChallengeCache) AcceptChallenge(ctx context.Context, challenge Challenge) bool {
	trace := ctx.Value("trace")

	key := fmt.Sprintf("%s,%s", challenge.Challenged.ID, challenge.Challenger.ID)

	item := c.cache.Get(challenge.Key())
	if item == nil {
		return false
	}

	stopChan := item.Value()
	if stopChan != nil {
		stopChan <- struct{}{}
	}

	slog.Info("accepted challenge from challenge Cache", "trace", trace, "key", key, "challenge", challenge)
	return true
}
