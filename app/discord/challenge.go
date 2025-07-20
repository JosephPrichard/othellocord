package discord

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
	return fmt.Sprintf("%s,%s", c.Challenged.Id, c.Challenger.Id)
}

type ChalValue struct {
	stopChan   chan struct{}
	expireChan chan struct{}
}

type ChallengeCache struct {
	cache *ttlcache.Cache[string, ChalValue]
}

func NewChallengeCache() ChallengeCache {
	return ChallengeCache{
		cache: ttlcache.New[string, ChalValue](),
	}
}

var ChallengeTTl = time.Second * 60

func (c ChallengeCache) CreateChallenge(ctx context.Context, challenge Challenge, expireChan chan struct{}) {
	trace := ctx.Value("trace")

	stopChan := make(chan struct{}, 1)

	key := challenge.Key()
	_ = c.cache.Set(key, ChalValue{stopChan: stopChan, expireChan: expireChan}, ChallengeTTl)
	slog.Info("set challenge into challenge cache", "trace", trace, "key", key, "challenge", challenge)

	go func() {
		defer close(expireChan)
		defer c.cache.Delete(key)

		timer := time.NewTimer(ChallengeTTl)
		select {
		case <-timer.C:
			slog.Info("expired challenge", "trace", trace, "key", key, "challenge", challenge)
			expireChan <- struct{}{}
			return
		case <-stopChan:
			slog.Info("stopped challenge", "trace", trace, "key", key, "challenge", challenge)
			return
		}
	}()
}

func (c ChallengeCache) AcceptChallenge(ctx context.Context, challenge Challenge) bool {
	trace := ctx.Value("trace")

	key := fmt.Sprintf("%s,%s", challenge.Challenged.Id, challenge.Challenger.Id)

	item := c.cache.Get(challenge.Key())
	if item == nil {
		return false
	}

	value := item.Value()
	if value.stopChan != nil {
		value.stopChan <- struct{}{}
	}

	slog.Info("accepted challenge from challenge cache", "trace", trace, "key", key, "challenge", challenge)
	return true
}
