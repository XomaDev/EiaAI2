package space.themelon.eia64.structs

import space.themelon.eia64.Expression
import space.themelon.eia64.syntax.Token

data class Property(val token: Token, val name: String, val value: Expression)