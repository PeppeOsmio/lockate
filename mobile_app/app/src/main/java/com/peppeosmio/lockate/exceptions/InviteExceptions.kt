package com.peppeosmio.lockate.exceptions

/** No invite has this number: it's mistyped, expired or was cancelled */
class InviteNotFoundException : Exception()

/** Someone else already answered this invite */
class InviteAlreadyClaimedException : Exception()

/** The inviter didn't accept the reply, so the words were wrong, or the invite got cancelled */
class InviteRejectedException : Exception()

class InviteExpiredException : Exception()

class TooManyRequestsException : Exception()
