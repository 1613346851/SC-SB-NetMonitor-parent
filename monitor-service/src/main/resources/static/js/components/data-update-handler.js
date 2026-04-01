(function() {
    'use strict';

    const DataUpdateHandler = {
        stompClient: null,
        connected: false,
        subscriptions: {},
        reconnectAttempts: 0,
        maxReconnectAttempts: 5,
        reconnectDelay: 3000,

        connect: function(onConnected, onDisconnected) {
            const self = this;
            
            if (this.connected) {
                if (onConnected) onConnected();
                return;
            }

            this._initWebSocket(function(success) {
                if (!success) {
                    console.error('WebSocket资源加载失败');
                    if (onDisconnected) onDisconnected();
                    return;
                }
                
                try {
                    const socket = new SockJS('/ws/alert');
                    self.stompClient = Stomp.over(socket);
                    
                    self.stompClient.debug = null;
                    
                    self.stompClient.connect({}, 
                        function(frame) {
                            self.connected = true;
                            self.reconnectAttempts = 0;
                            console.log('数据更新WebSocket已连接');
                            if (onConnected) onConnected();
                        },
                        function(error) {
                            self.connected = false;
                            console.error('数据更新WebSocket连接错误:', error);
                            if (onDisconnected) onDisconnected();
                            self.scheduleReconnect(onConnected, onDisconnected);
                        }
                    );
                } catch (e) {
                    console.error('创建WebSocket连接失败:', e);
                    self.scheduleReconnect(onConnected, onDisconnected);
                }
            });
        },

        _initWebSocket: function(callback) {
            if (window.SockJS && window.Stomp) {
                callback(true);
                return;
            }
            
            if (typeof ResourceLoader !== 'undefined') {
                ResourceLoader.loadWebSocket().then(function() {
                    callback(true);
                }).catch(function(e) {
                    console.error('加载WebSocket资源失败:', e);
                    callback(false);
                });
            } else {
                console.warn('ResourceLoader未定义，无法加载WebSocket资源');
                callback(false);
            }
        },

        scheduleReconnect: function(onConnected, onDisconnected) {
            const self = this;
            if (this.reconnectAttempts < this.maxReconnectAttempts) {
                this.reconnectAttempts++;
                console.log(`尝试重连数据更新WebSocket (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
                setTimeout(function() {
                    self.connect(onConnected, onDisconnected);
                }, this.reconnectDelay);
            }
        },

        subscribe: function(topic, callback) {
            if (!this.connected || !this.stompClient) {
                console.warn('WebSocket未连接，无法订阅:', topic);
                return null;
            }

            if (this.subscriptions[topic]) {
                return this.subscriptions[topic];
            }

            const subscription = this.stompClient.subscribe(topic, function(message) {
                try {
                    const data = JSON.parse(message.body);
                    callback(data);
                } catch (e) {
                    console.error('解析WebSocket消息失败:', e);
                }
            });

            this.subscriptions[topic] = subscription;
            return subscription;
        },

        unsubscribe: function(topic) {
            if (this.subscriptions[topic]) {
                this.subscriptions[topic].unsubscribe();
                delete this.subscriptions[topic];
            }
        },

        disconnect: function() {
            if (this.stompClient) {
                Object.keys(this.subscriptions).forEach(topic => {
                    this.subscriptions[topic].unsubscribe();
                });
                this.subscriptions = {};
                
                try {
                    this.stompClient.disconnect();
                } catch (e) {
                    console.warn('断开WebSocket连接时出错:', e);
                }
            }
            this.connected = false;
        },

        onDataUpdate: function(callback) {
            return this.subscribe('/topic/data', callback);
        },

        handleAttackRecord: function(data, tableInstance) {
            if (!tableInstance || !data) return;
            
            const currentData = tableInstance.getCurrentData() || [];
            const existingIndex = currentData.findIndex(item => item.id === data.id);
            
            if (existingIndex >= 0) {
                currentData[existingIndex] = data;
            } else {
                currentData.unshift(data);
                if (currentData.length > tableInstance.pageSize) {
                    currentData.pop();
                }
            }
            
            tableInstance.updateData(currentData, false);
        },

        handleAlertRecord: function(data, tableInstance) {
            if (!tableInstance || !data) return;
            
            const currentData = tableInstance.getCurrentData() || [];
            const existingIndex = currentData.findIndex(item => item.id === data.id);
            
            if (existingIndex >= 0) {
                currentData[existingIndex] = data;
            } else {
                currentData.unshift(data);
                if (currentData.length > tableInstance.pageSize) {
                    currentData.pop();
                }
            }
            
            tableInstance.updateData(currentData, false);
        }
    };

    window.DataUpdateHandler = DataUpdateHandler;
})();
