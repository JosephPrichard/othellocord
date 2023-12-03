/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.challenge;

import services.player.Player;

public record Challenge(Player challenged, Player challenger) {

    @Override
    public String toString() {
        return "Challenge{" +
            "challenged=" + challenged +
            ", challenger=" + challenger +
            '}';
    }
}
