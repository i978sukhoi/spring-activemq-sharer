package com.github.i978sukhoi.spring.activemq.sharer

interface SharedQueueRegisterer {
    /**
     * register queue sharing
     * @param src source queue name
     * @param dest destination queue name
     */
    fun register(src: String, dest: String)

    /**
     * @param src source queue name
     * @param dest destination queue name
     */
    fun deregister(src: String, dest: String)
}
