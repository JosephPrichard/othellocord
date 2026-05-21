package app

import (
	"context"
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

const InternalServerErrorMsg = "An unexpected error occurred."

func handleResponseSend(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate, resp HandlerResponse) {
	switch resp := resp.(type) {
	case ChannelMessageSendResponse:
		channelMessageSend(ctx, dg, resp.ChannelID, resp.Message)
	case ChannelMessageSendComplexResponse:
		channelMessageSendComplex(ctx, dg, resp.ChannelID, resp.Data)
	case InteractionResponse:
		interactionRespond(ctx, dg, ic.Interaction, resp.Response)
	case InteractionResponseEdit:
		interactionResponseEdit(ctx, dg, ic.Interaction, resp.Edit)
	case InteractionError:
		handleInteractionError(ctx, dg, ic, resp.Err)
	}
}

func channelMessageSend(ctx context.Context, dg *discordgo.Session, channelID string, str string) {
	if _, err := dg.ChannelMessageSend(channelID, str); err != nil {
		slog.Error("failed to send message", "err", err, "trace", ctx.Value(TraceKey))
	}
}

func channelMessageSendComplex(ctx context.Context, dg *discordgo.Session, channelID string, data *discordgo.MessageSend) {
	if _, err := dg.ChannelMessageSendComplex(channelID, data); err != nil {
		slog.Error("failed to send message complex", "err", err, "trace", ctx.Value(TraceKey))
	}
}

func interactionRespond(ctx context.Context, dg *discordgo.Session, i *discordgo.Interaction, r *discordgo.InteractionResponse) {
	if err := dg.InteractionRespond(i, r); err != nil {
		slog.Error("failed to send interaction response", "err", err, "trace", ctx.Value(TraceKey))
	}
}

func interactionResponseEdit(ctx context.Context, dg *discordgo.Session, i *discordgo.Interaction, e *discordgo.WebhookEdit) {
	if _, err := dg.InteractionResponseEdit(i, e); err != nil {
		slog.Error("failed to send interaction response edit", "err", err, "trace", ctx.Value(TraceKey))
	}
}

func handleInteractionError(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate, err error) {
	trace := ctx.Value(TraceKey)
	slog.Error("error when handling command", "trace", trace, "err", err)

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
	if err := dg.InteractionRespond(ic.Interaction, resp); err != nil {
		slog.Error("failed to respond interaction error", "err", err)
	}
}
