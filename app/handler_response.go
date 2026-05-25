package app

import (
	"context"
	"encoding/json"
	"github.com/bwmarrin/discordgo"
	"log/slog"
)

type HandlerResponse interface {
	isHandlerResponse()
}

type ChannelMessageSendResponse struct {
	ChannelID string
	Message   string
}

func (r ChannelMessageSendResponse) isHandlerResponse() {}

type ChannelMessageSendComplexResponse struct {
	ChannelID string
	Data      *discordgo.MessageSend
}

func (r ChannelMessageSendComplexResponse) isHandlerResponse() {}

type InteractionResponse struct {
	Response *discordgo.InteractionResponse
}

func (r InteractionResponse) isHandlerResponse() {}

type InteractionResponseEdit struct {
	Edit *discordgo.WebhookEdit
}

func (r InteractionResponseEdit) isHandlerResponse() {}

type InteractionError struct {
	Err error
}

func (r InteractionError) isHandlerResponse() {}

type ManyResponses struct {
	Responses []HandlerResponse
}

func (r ManyResponses) isHandlerResponse() {}

func MakeManyResponses(responses ...HandlerResponse) ManyResponses {
	return ManyResponses{Responses: responses}
}

const InternalServerErrorMsg = "An unexpected error occurred."

//go:generate mockgen -source=handler_response.go -destination=./handler_response_mock.go -package=app
type DiscordAPI interface {
	ChannelMessageSend(channelID string, content string, options ...discordgo.RequestOption) (st *discordgo.Message, err error)
	ChannelMessageSendComplex(channelID string, data *discordgo.MessageSend, options ...discordgo.RequestOption) (st *discordgo.Message, err error)
	InteractionRespond(interaction *discordgo.Interaction, resp *discordgo.InteractionResponse, options ...discordgo.RequestOption) error
	InteractionResponseEdit(interaction *discordgo.Interaction, newresp *discordgo.WebhookEdit, options ...discordgo.RequestOption) (*discordgo.Message, error)
}

func handleResponseSend(ctx context.Context, discord DiscordAPI, ic *discordgo.InteractionCreate, resp HandlerResponse) {
	switch resp := resp.(type) {
	case ChannelMessageSendResponse:
		channelMessageSend(ctx, discord, resp.ChannelID, resp.Message)
	case ChannelMessageSendComplexResponse:
		channelMessageSendComplex(ctx, discord, resp.ChannelID, resp.Data)
	case InteractionResponse:
		interactionRespond(ctx, discord, ic.Interaction, resp.Response)
	case InteractionResponseEdit:
		interactionResponseEdit(ctx, discord, ic.Interaction, resp.Edit)
	case InteractionError:
		handleInteractionError(ctx, discord, ic, resp.Err)
	case ManyResponses:
		for _, r := range resp.Responses {
			handleResponseSend(ctx, discord, ic, r)
		}
	}
}

func marshalJson(v interface{}) string {
	b, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return string(b)
}

func channelMessageSend(ctx context.Context, discord DiscordAPI, channelID string, str string) {
	slog.InfoContext(ctx, "sending message to channel", "channelID", channelID, "response", str)

	if _, err := discord.ChannelMessageSend(channelID, str); err != nil {
		slog.ErrorContext(ctx, "failed to send message", "err", err)
	}
}

func channelMessageSendComplex(ctx context.Context, discord DiscordAPI, channelID string, data *discordgo.MessageSend) {
	slog.InfoContext(ctx, "sending complex message to channel", "channelID", channelID, "response", marshalJson(data))

	if _, err := discord.ChannelMessageSendComplex(channelID, data); err != nil {
		slog.ErrorContext(ctx, "failed to send message complex", "err", err)
	}
}

func interactionRespond(ctx context.Context, discord DiscordAPI, i *discordgo.Interaction, r *discordgo.InteractionResponse) {
	slog.InfoContext(ctx, "sending interaction response", "response", marshalJson(r))

	if err := discord.InteractionRespond(i, r); err != nil {
		slog.ErrorContext(ctx, "failed to send interaction response", "err", err)
	}
}

func interactionResponseEdit(ctx context.Context, discord DiscordAPI, i *discordgo.Interaction, e *discordgo.WebhookEdit) {
	slog.InfoContext(ctx, "sending interaction response edit", "response", marshalJson(e))

	if _, err := discord.InteractionResponseEdit(i, e); err != nil {
		slog.ErrorContext(ctx, "failed to send interaction response edit", "err", err)
	}
}

func handleInteractionError(ctx context.Context, discord DiscordAPI, ic *discordgo.InteractionCreate, err error) {
	slog.ErrorContext(ctx, "error when handling command", "err", err)

	content := InternalServerErrorMsg

	switch err.(type) {
	case *SubCmdError, *OptionError:
		content = err.Error()
	}

	resp := &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: content,
		},
	}
	if err := discord.InteractionRespond(ic.Interaction, resp); err != nil {
		slog.ErrorContext(ctx, "failed to respond interaction error", "err", err)
	}
}
