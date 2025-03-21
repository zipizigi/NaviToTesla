package me.zipi.navitotesla.exception

class ForbiddenException(
    private val httpCode: Int,
    message: String?,
) : RuntimeException(message) {
    override fun toString(): String = super.toString() + " httpStatus: " + httpCode
}
