(ns status-im.test.console
  (:require [clojure.test :refer :all]
            [status-im.accessibility-ids :as id]
            [status-im.test.appium :refer :all]))

(defaction send-command []
  (click :send-command))

(defaction respond-to-request [request value]
  (click (keyword (str "request-" (name request))))
  (write :input value)
  (send-command))

(defaction confirm-password [value]
  (write :input value)
  (send-command))

(appium-test sign-up-without-phone
  (respond-to-request :password "password")
  (confirm-password "password")
  (click id/toolbar-back-button)
  (contains-text "Chats"))

(appium-test wrong-password
  (respond-to-request :password "abc")
  (contains-text "Password should be not less then 6 symbols.")
  (click :cancel-response-button)
  (respond-to-request :password "password")
  (confirm-password "abc")
  (contains-text "Password confirmation doesn't match password."))

(appium-test wrong-phone-number
  (respond-to-request :password "password")
  (confirm-password "password")
  (respond-to-request :phone "1234")
  (contains-text "Invalid phone number"))

(appium-test wrong-confirmation-code
  (respond-to-request :password "password")
  (confirm-password "password")
  (respond-to-request :phone "+380671111111")
  (respond-to-request :confirmation-code "432")
  (contains-text "Wrong format")
  (click :cancel-response-button)
  (respond-to-request :confirmation-code "4321")
  (contains-text "Wrong confirmation code"))
