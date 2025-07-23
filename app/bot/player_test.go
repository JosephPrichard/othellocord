package bot

import (
	"context"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/stretchr/testify/assert"
	"testing"
)

type MockUserFetcher struct{}

func (uf *MockUserFetcher) User(userID string, _ ...discordgo.RequestOption) (user *discordgo.User, err error) {
	if userID == "1" {
		return &discordgo.User{ID: "1", Username: "Player1"}, nil
	}
	panic(fmt.Errorf("unexpected playerId in mock user fetcher: %s", userID))
}

func TestUserCache_GetUsername(t *testing.T) {
	uc := NewUserCache(&MockUserFetcher{})

	ctx := context.WithValue(context.Background(), "trace", "test-user-Cache")
	username, err := uc.GetUsername(ctx, "1")
	if err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, "Player1", username)

	user := uc.Cache.Get("1")
	assert.NotNil(t, user)
	assert.Equal(t, discordgo.User{ID: "1", Username: "Player1"}, user.Value())
}
