(ns status-im.debug.handlers
  (:require [re-frame.core :refer [after dispatch]]
            [status-im.utils.handlers :refer [register-handler] :as u]
            [taoensso.timbre :as log]))

(register-handler :debug-add-dapp
  (u/side-effect!
    (fn [{:keys [contacts]} [_ {:keys [name whisper-identity dapp-url] :as dapp-data}]]
      (when (and name
                 whisper-identity
                 dapp-url
                 (or (not (get contacts whisper-identity))
                     (get-in contacts [whisper-identity :debug?])))
        (let [dapp (merge dapp-data {:dapp?  true
                                     :debug? true})]
          (dispatch [:add-chat whisper-identity {:name   name
                                                 :debug? true}])
          (dispatch [:add-contacts [dapp]]))))))

(register-handler :debug-remove-dapp
  (u/side-effect!
    (fn [_ [_ {:keys [whisper-identity]}]]
      (dispatch [:remove-chat whisper-identity])
      (dispatch [:remove-contact whisper-identity #(and (:dapp? %) (:debug? %))]))))

(register-handler :debug-dapp-changed
  (u/side-effect!
    (fn [{:keys [webview-bridge current-chat-id chats]} [_ {:keys [whisper-identity]}]]
      (when (and (= current-chat-id whisper-identity)
                 (get-in chats [whisper-identity :debug?])
                 webview-bridge)
        (.reload webview-bridge)))))

(register-handler :received-debug-message
  (u/side-effect!
    (fn [{:keys [web3]} [_ {:keys [action args]}]]
      (let [args (.toAscii web3 args)
            json (.parse js/JSON args)
            obj  (js->clj json :keywordize-keys true)]
        (case action
          :add-dapp (dispatch [:debug-add-dapp obj])
          :remove-dapp (dispatch [:debug-remove-dapp obj])
          :dapp-changed (dispatch [:debug-dapp-changed obj])
          :default)))))
