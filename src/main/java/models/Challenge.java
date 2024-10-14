/*
 * Copyright (c) Joseph Prichard 2024.
 */

package models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Challenge {
    public Player challenged;
    public Player challenger;
}
