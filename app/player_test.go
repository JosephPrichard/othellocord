package app

import (
	"context"
	"testing"

	"github.com/bwmarrin/discordgo"
	"github.com/stretchr/testify/assert"
	"go.uber.org/mock/gomock"
)

func TestUserCache_GetUsername(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	fetcher := NewMockUserFetcher(ctrl)

	fetcher.EXPECT().
		User(gomock.Eq("id1"), gomock.Any()).
		Return(&discordgo.User{ID: "id1", Username: "Player1"}, nil)

	userCache := MakeUserCache(fetcher)

	ctx := context.WithValue(context.Background(), TraceKey, "test-user-Cache")
	username, err := userCache.GetUsername(ctx, "id1")
	if err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, "Player1", username)

	user := userCache.internal.Get("id1")
	assert.NotNil(t, user)
	assert.Equal(t, discordgo.User{ID: "id1", Username: "Player1"}, *user.Value())
}
