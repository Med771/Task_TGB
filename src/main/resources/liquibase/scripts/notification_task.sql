CREATE TABLE notification_tasks (
    id SERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    task TEXT NOT NULL,
    data_time TIMESTAMP NOT NULL
);