"""RabbitMQ 消费者：后台线程 + 手动 ack。"""
import logging
import threading
import time

import pika

from app.config import Settings
from app.pipeline import handle_message

logger = logging.getLogger(__name__)


class ResumeConsumer(threading.Thread):
    def __init__(self, settings: Settings):
        super().__init__(name="mineru-consumer", daemon=True)
        self.settings = settings
        self._stop_event = threading.Event()
        self._connection: pika.BlockingConnection | None = None
        self._channel = None

    def run(self) -> None:
        while not self._stop_event.is_set():
            try:
                self._consume()
            except Exception as exc:
                if self._stop_event.is_set():
                    break
                logger.warning("RabbitMQ 消费中断，5 秒后重连: %s", exc)
                time.sleep(5)

    def _consume(self) -> None:
        credentials = pika.PlainCredentials(
            self.settings.rabbitmq_user, self.settings.rabbitmq_password
        )
        parameters = pika.ConnectionParameters(
            host=self.settings.rabbitmq_host,
            port=self.settings.rabbitmq_port,
            credentials=credentials,
            heartbeat=60,
            blocked_connection_timeout=30,
        )
        self._connection = pika.BlockingConnection(parameters)
        self._channel = self._connection.channel()
        self._channel.basic_qos(prefetch_count=1)
        # 队列由 resume-service 声明（带 DLX 参数）；这里不重复 declare，避免参数不一致
        self._channel.basic_consume(
            queue=self.settings.mineru_queue,
            on_message_callback=self._on_message,
            auto_ack=False,
        )
        logger.info("开始消费 %s", self.settings.mineru_queue)
        self._channel.start_consuming()

    def _on_message(self, channel, method, properties, body) -> None:
        try:
            handle_message(body.decode("utf-8"), self.settings)
            channel.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            logger.exception("消息处理失败，拒绝并转死信队列")
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def stop(self) -> None:
        self._stop_event.set()
        try:
            if self._channel is not None and self._channel.is_open:
                self._channel.stop_consuming()
        except Exception:
            pass
        try:
            if self._connection is not None and self._connection.is_open:
                self._connection.close()
        except Exception:
            pass
