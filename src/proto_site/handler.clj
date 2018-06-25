(ns proto-site.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :refer [redirect]]
            [clojure.string :refer [join split]]
            [proto-site.middlewares :refer [wrap-base]]
            [proto-site.layout :as html]
            [proto-site.utils :as util :refer [defhandler]]
            [clj-wiite.core :as w])
  (:import java.util.Date))

(def req-deb (atom []))

(def posts-per-page 5)

(def last-insert-rowid
  (keyword "last_insert_rowid()"))

(defn create-watom []
  (let [state (w/watom "postgres://wiiteuser:passu@127.0.0.1:5432/wiitetest")]
    (when (nil? @state)
      (reset! state []))
    state))

(defonce posts (create-watom))

(defn id->post&tags [id]
  (some #(when (= (:id %) id) %) @posts))

(defn posts-range [n offset]
  (if (> (+ n offset) (count @posts))
    (subvec @posts offset)
   (subvec @posts n (+ n offset))))

;; First page is no."1"
(defhandler posts-in-page [p :as-int flash]
  (let [p (or p 1)
        targets (posts-range posts-per-page (* (dec p) posts-per-page))
        n-posts (count @posts)
        max-page (inc (quot n-posts posts-per-page))]
    (html/blog-list targets p max-page flash)))

(defhandler post-detail [id :as-int]
  (let [post (id->post&tags id)]
    (html/blog-post post)))

(defn split-tags [tags-str]
  (distinct (split tags-str #"\s")))

(defn save-new-post! [title content tags]
  (let [now-epoc (util/now->epoc)]
    (swap! posts conj {:id (if (empty? @posts)
                             1
                             (apply max (map :id @posts)))
                       :title title
                       :created-at now-epoc
                       :content content
                       :tags (split-tags tags)})
    (println "New post arrived!")
    (util/redirect-with-flash "/" {:message "Posted new article!"})))

(defn validate-new-post [title content tags auth]
  (cond-> []
    (empty? title) (conj "Title is empty")
    (empty? content) (conj "Content is empty")
    (not auth) (conj "Wrong password, maybe")))

(defn illegal-post [title content tags messages]
  (let [flash {:dtitle title
               :dcontent content
               :dtas tags
               :errors messages}]
    (util/redirect-with-flash "/new" flash)))

(defhandler handle-new-post [title content tags auth]
  (if-let [errors (seq (validate-new-post title content tags auth))]
    (illegal-post title content tags (join "." errors))
    (save-new-post! title content tags)))

(defn new-post-form [flash]
  (html/blog-new-article (anti-forgery-field)
                         flash))

(defhandler delete-post! [id :as-int auth]
  (if (and auth id)
    (do
      (reset! posts (filterv #(not= (:id %) id) @posts))
      (println "Deleted:" id)
      (util/redirect-with-flash "/" {:message (str "Deleted post:" id)}))
    (util/redirect-with-flash (str "/del?id=" id)
                              {:error "Wrong password!!"})))

(defhandler edit-post-form [id :as-int flash]
  (let [the-post (id->post&tags id)]
    (html/edit-post (anti-forgery-field) the-post (:error flash))))

(defn find-index-of [f coll]
  (when-let [item (first
                    (filter #(f (second %)) (map-indexed vector coll)))]
    (first item)))

(defhandler update-post! [id :as-int title content tags auth] ;; TODO: Need refactoring
  (if-let [errors (seq (validate-new-post title content tags auth))]
    (util/redirect-with-flash (str "/edit?id=" id) ;; send back to edit-post-form
                              {:error (str "ERROR:" (join "/" errors))})
    (let [post (id->post&tags id)
          index (find-index-of #(= (:id %) id) @posts)
          new-tags (split-tags tags)]
      (swap! posts assoc-in [index :tags] new-tags)
      (swap! posts assoc-in [index :date] (util/now->epoc))
      (util/redirect-with-flash "/"
                                {:message (str "Updated post! id:" id)}))))

(defn search-by [f]
  (let [result (filter #(f %) @posts)]
      (html/search-result result "")))


(defhandler search-by-date [year :as-int month :as-int date :as-int]
  (if (and date (not month))
    "TODO: ERROR"
    (let [[from til] (util/date-range year month date)
          posts (filter #(and (>= (:date %) from) (<= (:til %) til)))]
      (html/search-result posts (str "Posted at " year
                                     (when month "-" month)
                                     (when date "-" date))))))

(defroutes app-routes
  (GET "/" [p :as {flash :flash}] (posts-in-page p flash))
  (GET "/article/:id" [id] (html/blog-post (id->post&tags id)))
  (GET "/new" {flash :flash} (new-post-form flash))
  (POST "/new" [title content tags auth] (handle-new-post title content tags auth))
  (GET "/del" [id :as {flash :flash}] (html/blog-delete (anti-forgery-field)
                                             (merge flash
                                                    (id->post&tags id))))
  (DELETE "/del" [id auth] (delete-post! id auth))
  (GET "/edit" [id :as {flash :flash}] (edit-post-form id flash))
  (PUT "/edit" [id title content tags auth] (update-post! id title content tags auth))
  (GET "/search/date" [year month date] (search-by-date year month date))
  (GET "/search/text" [q] (search-by
                            #(or (.contains (:title %) q)
                                 (.contains (:content %)))))
  (GET "/search/tag" [q] (search-by
                            #(some (fn [x] (when (= x q) true)) (:tags %))))
  (ANY "/debug/echo" req (pr-str (swap! req-deb conj req)))
  (route/not-found "Not Found")
  (route/resources "/"))

(def app
  (wrap-base app-routes))
