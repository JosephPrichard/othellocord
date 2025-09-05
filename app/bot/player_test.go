package bot

import (
	"context"
	"fmt"
	"testing"

	"github.com/bwmarrin/discordgo"
	"github.com/stretchr/testify/assert"
)

type MockUserFetcher struct{}

func (mock *MockUserFetcher) User(userID string, _ ...discordgo.RequestOption) (user *discordgo.User, err error) {
	switch userID {
	case "id1":
		return &discordgo.User{ID: "id1", Username: "Player1"}, nil
	case "id2":
		return &discordgo.User{ID: "id2", Username: "Player2"}, nil
	case "id3":
		return &discordgo.User{ID: "id3", Username: "Player3"}, nil
	case "id4":
		return &discordgo.User{ID: "id4", Username: "Player4"}, nil
	case "id5":
		return &discordgo.User{ID: "id5", Username: "Player5"}, nil
	case "id6":
		return &discordgo.User{ID: "id6", Username: "Player6"}, nil
	case "id7":
		return &discordgo.User{ID: "id7", Username: "Player7"}, nil
	}
	return nil, fmt.Errorf("unexpected playerId in mock user fetcher: %s", userID)
}

func TestUserCache_GetUsername(t *testing.T) {
	uc := NewUserCache(&MockUserFetcher{})

	ctx := context.WithValue(context.Background(), TraceKey, "test-user-Cache")
	username, err := uc.GetUsername(ctx, "id1")
	if err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, "Player1", username)

	user := uc.Cache.Get("id1")
	assert.NotNil(t, user)
	assert.Equal(t, discordgo.User{ID: "id1", Username: "Player1"}, user.Value())
}
