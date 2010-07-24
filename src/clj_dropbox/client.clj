(ns clj-dropbox.client
  (:require
   [clojure.java [io :as io]]
   [clojure.contrib [json :as cc.json]]
   [clj-dropbox [constants :as constants] [oauth :as oauth]])
  (:import
   [org.apache.http.entity.mime MultipartEntity]
   [org.apache.http.entity.mime.content StringBody FileBody InputStreamBody]
   [org.apache.james.mime4j.message BodyPart]))

(declare url-decode-content ensure-content)

;;; Making a consumer and user connection ;;;

(defn  new-consumer
  "Returns a dropbox client representing the
  consumer. You need to make a user connection
  before performing any API calls. You should
  only need one client per-program. "
  [consumer-key consumer-secret]
  (oauth/get-consumer consumer-key consumer-secret))

(defn request-user-authorization
  "Returns [authorization-url request-callback],
  where authorization-url is the url where the
  user allows access, once user clicks
  \"allow\" on this page, you can request the user key and
  secret. The request-callback is the
  no-arg function you call once the user approves
  (request-callback) should return [user-key user-secret]
  if everything worked out. You get an exception otherwise.

  The other-params are passed into requet-url in case you
  want a callback-url associated with autorization which
  the user is directed to after giving you access. In that
  case you should call

  The variant with the callback-uri is where the user
  will be directed after aproval is given."
  ([consumer callback-uri]
     (let [oauth-token (oauth/request-token consumer)]
       [(oauth/approval-uri consumer (:oauth_token oauth-token) callback-uri)
	(fn []
	  (let [response (oauth/access-token-response consumer oauth-token callback-uri)
		{:keys [oauth_token,oauth_token_secret]}
		  (-> response ensure-content url-decode-content)]
	    [oauth_token oauth_token_secret]))]))
  ([consumer] (request-user-authorization consumer nil)))

(defn  new-user-client
  "Returns a connection for a user you can use for
  API calls.
  args: [consumer user-key user-secret]
  consumer: from new-consumer
  user-key, user-secret: come from request-user-authorization callback
  options:
     :mode {:dropbox, :sandbox default: :dropbox}
  This dictates the root for all drobbox operations. :dropbox
  is relative to the user's dropbox root. :sandbox is to an app-specific
  folder root."
  [consumer user-key user-secret &
   {:keys [mode] :or {mode :dropbox}}]
  (oauth/make-connection consumer user-key user-secret {:mode mode}))

;;; API Calls ;;;

(def  *client* nil)

(defn user-info
  "returns map of user data"
  ([client]
     (-> ((:req-fn client) :GET  (str constants/+drbx-api+ "account/info") {})
	 ensure-content
	 cc.json/read-json))
  ([]
     (user-info *client*)))

(defn get-file
  "returns content of file at path as a string on success. on error,
  throws exception with server reason for denial"
  ([client path]
     (let [mode (-> client :opts :mode)]
       (-> ((:req-fn client) :GET (str (constants/drbx-files-host mode) path) {})
	   ensure-content)))
  ([path] (get-file *client* path)))

(defn upload-file
  "Upload a local file to user dropbox.
   remote-dir: is the dropbox remote directory you want the file in.
   If the :mode option is :dropbox [see new-user-client-client],
   the remote-dir is relative to the root is the user's dropbox, if it is :sandbox,
   it is relative to the  app-specific folder. In either case, use the \"\"
   argument for the root.
   local-path: is either a file or string denoting a local file. The
   uploaded file takes the same file name as the local file. Can't get
   the HttpPost to respect the parameter which is meant to control
   the name.

   NOTE: It seems like Dropbox will create the directory structure
   of the remote path if it doesn't exist."
  ([client local-path remote-dir]
     (let [mode (-> client :opts :mode)
	   local-file (io/file local-path) file-name (.getName local-file)]
        (-> ((:req-fn client) :POST (str (constants/drbx-files-host mode) remote-dir)
	    {:file file-name}
	    :BODY (doto (MultipartEntity.)
		    (.addPart "file" (FileBody. local-file))))
	   ensure-content)))
  ([remote-dir local-path] (upload-file *client* remote-dir local-path)))

(defn list-files
  "returns a seq of file metadata for the given
   directory. Each file metadata supports keys
      :path   absolute dropbox-relative path
      :bytes  byte size (in int)
      :modified date-time last modified
      :is_dir is directory?
      :icon  dropbox-icon
      :size  file size string"
  ([client path]
     (let [path (if (.startsWith #^String path "/")
		  (.replaceAll #^String path "^/+" "")
		  path)
	   mode (-> client :opts :mode)]
       (-> ((:req-fn client) :GET (str (constants/drbx-metadata-host mode) path)
		   {:list  "true"})
	   ensure-content
	   cc.json/read-json
	   :contents)))
  ([path] (list-files *client* path)))

(defn copy-file
  "copy file from-path to-path. on success
   returns meta-data of new file (see list-files
   for metadata strucutre), throws exception
   with hopefully useful error message.

  Both paths are assumed to be relative to the
  root determined by the mode {:dropbox, :sandbox}
  (see new-user-client for details)."
  ([client from-path to-path]
     (let [root (-> client :opts :mode str (.substring 1))]
       (->
	((:req-fn client) :GET constants/+drbx-copy-file+
	 {:from_path from-path :to_path to-path :root root})
	ensure-content
	cc.json/read-json)))
  ([from-path to-path] (copy-file *client* from-path to-path)))

(defn move-file
  "move file from-path to-path. on success
   returns meta-data of moved file, throws exception
   with hopefully useful error message.

  Both paths are assumed to be relative to the
  root determined by the mode {:dropbox, :sandbox}
  (see new-user-client for details)."
  ([client from-path to-path]
     (let [root (-> client :opts :mode str (.substring 1))]
       (->
	((:req-fn client) :GET constants/+drbx-move-file+
	 {:from_path from-path :to_path to-path :root root})
	ensure-content
	cc.json/read-json)))
  ([from-path to-path] (move-file *client* from-path to-path)))

(defn create-folder
  "create folder at path. on success returns
   folder metadata (see list-files for metadata info).
   On error throws exception."
  ([client path]
     (let [root (-> client :opts :mode str (.substring 1))]
       (->
	((:req-fn client) :GET constants/+drbx-create-folder+
	 {:path path :root root})
	ensure-content
	cc.json/read-json)))
  ([path] (create-folder *client* path)))

(defn delete-file
  "delete file at path (can be either a file or
   folder so be careful). on success
   returns nil. on error throws exception.
  (see new-user-client for details)."
  ([client path]
     (let [root (-> client :opts :mode str (.substring 1))]
       (->
	((:req-fn client) :GET constants/+drbx-delete+
	 {:path path :root root})
	ensure-content
	((constantly  nil)))))
  ([path] (delete-file *client* path)))

(defmacro with-user
  "binds parameter client to a thread-local *client*
   variable  so you can call all API operations
   {user-info,get-file,upload-file,...} without
   the client argument. "
  [client & body]
  `(binding [*client* ~client]
     ~@body))

;;; Private Functions ;;;

(defn- url-decode-content [s]
  (into {}
	(for [piece (seq (.split #^String s "&"))
	      :let [[k v] (.split #^String piece "=")]]
	  [(keyword k) v])))

(defn- ensure-content [response]
  (if (= (:code response) 200) (:content response)
      (throw (RuntimeException.
	      (str "Bad Response: " (:code response) "\n" (:content response)))))) 

;; (defn request-user-token-directly
;;   "Returns [user-key user-secret] for making
;;   a user connection. Dropbox lets you bypass
;;   the oauth dance once per user; so don't use this
;;   call too much. Store the results as well. "
;;   [client user-email user-password]
;;   nil)
