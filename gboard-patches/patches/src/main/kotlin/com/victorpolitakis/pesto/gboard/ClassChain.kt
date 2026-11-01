package com.victorpolitakis.pesto.gboard

/**
 * Walks a class and its superclasses, from [startType] up, and returns the first class for which
 * [declares] is true. The walk stops, returning null, at the first type [lookup] does not know,
 * which for an app is the first framework class (e.g. `Landroid/app/Application;`, not in the dex).
 *
 * Pure, so the walk is tested on the JVM without an APK; the patch passes the patcher's class
 * lookup.
 */
internal fun <C : Any> firstDeclaringClass(
    startType: String,
    lookup: (String) -> C?,
    superclassOf: (C) -> String?,
    declares: (C) -> Boolean,
): C? {
    val seen = mutableSetOf<String>()
    var type: String? = startType
    while (type != null && seen.add(type)) {
        val classDef = lookup(type) ?: return null
        if (declares(classDef)) return classDef
        type = superclassOf(classDef)
    }
    return null
}

/**
 * The fully qualified class name of a manifest component's `android:name`. As Android resolves it,
 * a name that starts with `.` or has no `.` is relative to the manifest [packageName].
 */
internal fun componentClassName(name: String, packageName: String): String = when {
    name.startsWith(".") -> packageName + name
    '.' !in name -> "$packageName.$name"
    else -> name
}

/** A Java class name as a type descriptor: `a.b.C` -> `La/b/C;`. */
internal fun classNameToType(className: String): String = "L" + className.replace('.', '/') + ";"
