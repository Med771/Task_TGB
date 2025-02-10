package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.entity.NotificationTaskEntity;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {
    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);
    private final TelegramBot telegramBot;
    private final NotificationTaskRepository notificationTaskRepository;

    private final String startCommandText = "/start";
    private final Pattern TASK_PATTERN = Pattern.compile("(?<dateTime>\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2})\\s+(?<task>.+)");
    private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");


    public TelegramBotUpdatesListener(TelegramBot telegramBot,
                                      NotificationTaskRepository notificationTaskRepository) {
        this.telegramBot = telegramBot;
        this.notificationTaskRepository = notificationTaskRepository;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Scheduled(fixedRate = 60000)
    public void checkAndSendNotifications() {
        String  now = LocalDateTime.now().format(FORMATTER);
        List<NotificationTaskEntity> tasks = notificationTaskRepository.findAll();

        for (NotificationTaskEntity task : tasks) {
            String dateTime = task.getDataTime().format(FORMATTER);

            if (dateTime.equals(now)) {
                this.sendMessage(task.getChatId(), task.getTask());

                notificationTaskRepository.delete(task);
            }
        }
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);

            Message message = update.message();

            if (message != null && message.text() != null) {
                String text = message.text();

                Chat chat = message.chat();
                Long chatId = chat.id();

                if (startCommandText.equals(text)) {
                    this.startCommand(chatId);
                }
                else {
                    this.taskCommand(chatId, text);
                }
            }
        });

        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void startCommand(Long chatId) {
        String responseText = "Привет! Я ваш Telegram-бот. Чем могу помочь?";

        this.sendMessage(chatId, responseText);
    }

    private void taskCommand(Long chatId, String text) {
        Matcher matcher = TASK_PATTERN.matcher(text);

        if (matcher.matches()) {
            String dateTime = matcher.group("dateTime");
            String task = matcher.group("task");

            String response = String.format("✅ Задача сохранена!\n📅 Дата и время: *%s*\n📌 Описание: *%s*", dateTime, task);
            sendMessage(chatId, response);

            NotificationTaskEntity notificationTask = new NotificationTaskEntity();

            notificationTask.setChatId(chatId);
            notificationTask.setTask(task);
            notificationTask.setDataTime(LocalDateTime.parse(dateTime, FORMATTER));

            notificationTaskRepository.save(notificationTask);
        } else {
            sendMessage(chatId, "❌ Ошибка! Отправьте сообщение в формате:\n`01.01.2022 20:00 Сделать домашнюю работу`");
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage response = new SendMessage(chatId, text).parseMode(ParseMode.Markdown);

        telegramBot.execute(response);
    }
}
