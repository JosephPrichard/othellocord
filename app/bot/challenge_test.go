package bot

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func init() {
	ChallengeTTl = time.Millisecond
}

func TestChallenge(t *testing.T) {
	cc := NewChallengeCache()

	ctx := context.WithValue(context.Background(), TraceKey, "test-challenge")
	challenge := Challenge{Challenged: Player{ID: "id1", Name: "name1"}, Challenger: Player{ID: "id2", Name: "name2"}}

	CreateChallenge(ctx, cc, challenge, func() {})
	didAccept := AcceptChallenge(ctx, cc, challenge)

	assert.True(t, didAccept)
}

func TestChallenge_Expiry(t *testing.T) {
	cc := NewChallengeCache()

	ctx := context.WithValue(context.Background(), TraceKey, "test-challenge")
	challenge := Challenge{Challenged: Player{ID: "id1", Name: "name1"}, Challenger: Player{ID: "id2", Name: "name2"}}

	expireChan := make(chan struct{}, 1)
	handleExpiry := func() {
		expireChan <- struct{}{}
	}

	CreateChallenge(ctx, cc, challenge, handleExpiry)

	select {
	case <-expireChan:
	case <-time.After(ChallengeTTl * 2):
		t.Fatal("challenge did not expire before timeout")
	}
}
