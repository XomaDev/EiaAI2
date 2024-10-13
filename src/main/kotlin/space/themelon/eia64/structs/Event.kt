package space.themelon.eia64.structs

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Signature

data class Event(val params: List<Pair<String, Signature>>, val expression: Expression)