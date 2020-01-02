package com.github.i978sukhoi.spring.activemq.sharer

/**
 * configuration message util
 */
object ConfigMessageUtil {
    /**
     * indicate register sharing
     */
    const val ADD = '+'
    /**
     * indicate de-register sharing
     */
    const val DEL = '-'
    /**
     * indicate investigation
     */
    const val INV = '?'
    /**
     * investigation message string
     */
    const val INV_MSG = "$INV;;"

    /**
     * [message] means investigation?
     */
    fun isInvestigate(message: String) = INV == message.getOrNull(0)

    /**
     * make shared-queue registration message
     */
    fun toAddMessage(src: String, dest: String) = "$ADD;$src;$dest"

    /**
     * make shared-queue de-registration message
     */
    fun toDelMessage(src: String, dest: String) = "$DEL;$src;$dest"

    /**
     * parse to [Triple]
     * - [Triple.first]: indicator. '?', '+', '-'
     * - [Triple.second]: source queue name
     * - [Triple.third]: destination queue name
     */
    fun parse(message: String) = message
        .split(';')
        .let {
            Triple(
                it.getOrNull(0)?.getOrNull(0) ?: INV,
                it.getOrNull(1) ?: "",
                it.getOrNull(2) ?: ""
            )
        }
}
