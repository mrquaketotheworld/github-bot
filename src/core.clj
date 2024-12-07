(ns core
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.util.response :refer [response]]))

(def telegram-token (System/getenv "TELEGRAM_TOKEN"))
(def chat-id (System/getenv "TELEGRAM_CHAT_ID"))

(defn send-telegram-message [message]
  (let [url (str "https://api.telegram.org/bot" telegram-token "/sendMessage")]
    (client/post url {:form-params {:chat_id chat-id
                                    :text message}})))

(defn handle-pull-request [payload]
  (let [{:keys [action pull_request requested_reviewer]} payload
        pr-url (:html_url pull_request)
        title (:title pull_request)
        source-branch (:head pull_request)
        target-branch (:base pull_request)
        source-branch-name (:ref source-branch)
        target-branch-name (:ref target-branch)
        user (:login (:user pull_request))]
    (cond
      (= action "opened")
      (send-telegram-message (str "📢 Новый пулл-реквест от " user
                                  "\nНазвание: " title
                                  "\nСсылка: " pr-url
                                  "\nИз ветки: " source-branch-name
                                  " → " target-branch-name))
      (= action "review_requested")
      (let [reviewer (:login requested_reviewer)]
        (send-telegram-message (str "👀 Назначен новый ревьюер: " reviewer
                                    "\nПулл-реквест: " title
                                    "\nСсылка: " pr-url)))

      (and (= action "closed") (:merged pull_request))
      (send-telegram-message (str "✅ Пулл-реквест замержен от " user
                                  "\nНазвание: " title
                                  "\nСсылка: " pr-url
                                  "\nВетки: " source-branch-name " → " target-branch-name)))))

(defn handle-review [payload]
  (let [{:keys [review pull_request]} payload
        review-state (:state review)
        pr-title (:title pull_request)
        pr-url (:html_url pull_request)
        reviewer (:login (:user review))]
    (case review-state
      "approved"
      (send-telegram-message (str "✅ Ревью принято от " reviewer
                                  "\nПулл-реквест: " pr-title
                                  "\nСсылка: " pr-url)))))

(defn handle-review-comment [payload]
  (let [{:keys [action comment pull_request]} payload
        commenter (:login (:user comment))
        comment-body (:body comment)
        pr-title (:title pull_request)
        pr-url (:html_url pull_request)]
    (when (= action "created")
      (send-telegram-message (str "💬 Новый комментарий от " commenter
                                  "\nПулл-реквест: " pr-title
                                  "\nТекст: " comment-body
                                  "\nСсылка: " pr-url)))))

(defn handle-review-thread [payload]
  (let [{:keys [action pull_request]} payload
        pr-title (:title pull_request)
        pr-url (:html_url pull_request)
        author (:login (:user pull_request))]
    (when (= action "resolved")
      (send-telegram-message (str "🔒 Обсуждение разрешено в PR от " author
                                  "\nПулл-реквест: " pr-title
                                  "\nСсылка: " pr-url)))))

(defn handle-webhook [request]
  (if (= (:request-method request) :post)
    (let [payload (json/parse-string (slurp (:body request)) true)
          event-type (get-in request [:headers "x-github-event"])]
      (case event-type
        "pull_request" (handle-pull-request payload)
        "pull_request_review" (handle-review payload)
        "pull_request_review_comment" (handle-review-comment payload)
        "pull_request_review_thread" (handle-review-thread payload)
        (println (str "⚠️ Неизвестное событие: " event-type)))
      (response "OK"))
    (response "Метод не поддерживается")))

(defn app [request]
  (case (:uri request)
    "/webhook" (handle-webhook request)
    (response "Not Found")))

(defn -main []
  (println "Сервер запущен на порту 3000")
  (run-jetty app {:port 3000}))

