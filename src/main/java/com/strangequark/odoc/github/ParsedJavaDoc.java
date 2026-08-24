package com.strangequark.odoc.github;

import java.util.List;

record ParsedJavaDoc(
        String packageName,
        String typeName,
        String typeKind,
        String documentation,
        List<JavaDocMember> members) {}
