package com.riaride.passes;

import java.util.UUID;

// A pass that can be used for a trip now.
public record UsablePass(UUID passId) {
}
