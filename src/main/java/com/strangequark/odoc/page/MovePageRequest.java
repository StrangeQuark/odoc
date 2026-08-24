package com.strangequark.odoc.page;

import java.util.UUID;

/** A null parent moves the page to the root of its current space. */
public record MovePageRequest(UUID parentId) {}
