package app

import (
	"context"
	"fmt"
	"log/slog"
	"strconv"
	"time"

	"github.com/bwmarrin/discordgo"
	"github.com/jellydator/ttlcache/v3"
)

const MinBotLevel = 1
const MaxBotLevel = 5

type Player struct {
	ID    string
	Name  string
	Level uint64 // only used for bot levels
}

func MakeHumanPlayer(user *discordgo.User) Player {
	return Player{ID: user.ID, Name: user.Username}
}

func MakeBotPlayer(level uint64) Player {
	return Player{ID: strconv.Itoa(int(level)), Name: fmt.Sprintf("NTest level %d", level), Level: level}
}

func MakePlayer(id string, name string) Player {
	var player Player

	if botId, err := strconv.Atoi(id); err == nil && IsValidBotLevel(uint64(botId)) {
		player = Player{ID: id, Name: fmt.Sprintf("NTest level %d", botId), Level: uint64(botId)}
	} else {
		player = Player{ID: id, Name: name}
	}

	return player
}

func LevelToSearchDepth(level uint64) uint64 {
	switch level {
	case 1:
		return 5
	case 2:
		return 8
	case 3:
		return 12
	case 4:
		return 15
	case 5:
		return 20
	}
	return 5
}

func (player Player) LevelToSearchDepth() uint64 {
	return LevelToSearchDepth(player.Level)
}

func (player Player) IsHuman() bool {
	return player.Level == 0
}

func (player Player) IsBot() bool {
	return player.Level != 0
}

func IsInvalidBotLevel(level uint64) bool {
	return level < MinBotLevel || level > MaxBotLevel
}

func IsValidBotLevel(level uint64) bool {
	return !IsInvalidBotLevel(level)
}

//go:generate mockgen -source=player.go -destination=./player_mock.go -package=app
type UserFetcher interface {
	User(userID string, options ...discordgo.RequestOption) (st *discordgo.User, err error)
}

type UserCache struct {
	internal    *ttlcache.Cache[string, *discordgo.User]
	userFetcher UserFetcher
}

func MakeUserCache(userFetcher UserFetcher) *UserCache {
	return &UserCache{internal: ttlcache.New[string, *discordgo.User](), userFetcher: userFetcher}
}

func (cache *UserCache) GetUsername(ctx context.Context, playerID string) (string, error) {
	user, err := cache.GetUser(ctx, playerID)
	if err != nil {
		return "", err
	}
	return user.Username, nil
}

func (cache *UserCache) GetPlayer(ctx context.Context, playerID string) (Player, error) {
	user, err := cache.GetUser(ctx, playerID)
	if err != nil {
		return Player{}, err
	}
	return MakeHumanPlayer(user), nil
}

const UserCacheTTl = time.Hour

func (cache *UserCache) GetUser(ctx context.Context, playerID string) (*discordgo.User, error) {
	var user *discordgo.User

	item := cache.internal.Get(playerID)
	if item != nil {
		user = item.Value()
	} else {
		u, err := cache.userFetcher.User(playerID, discordgo.WithContext(ctx))
		if err != nil {
			return nil, fmt.Errorf("fetch user from discord: %w", err)
		}
		user = u
		cache.internal.Set(playerID, user, UserCacheTTl)
		slog.InfoContext(ctx, "set user back into the cache", "user", user.Username, "player", playerID)
	}

	slog.InfoContext(ctx, "fetched user", "username", user.Username, "ID", playerID)
	return user, nil
}
