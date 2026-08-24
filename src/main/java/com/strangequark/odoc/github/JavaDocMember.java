package com.strangequark.odoc.github;

import java.util.List;

record JavaDocMember(
        String kind,
        String name,
        String signature,
        String documentation,
        List<JavaDocTag> tags) {}
