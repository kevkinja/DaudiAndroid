package com.zeroq.daudi4native.events

import com.zeroq.daudi4native.data.models.TruckModel

class QueueingEvent(val trucks: List<TruckModel>?, val error: Exception?)