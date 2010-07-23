(ns clj-dropbox.oauth
  (:require [clj-dropbox.constants :as constants]
            [oauth.client :as oauth]
	    [com.twinql.clojure [http :as http]]))

(defn get-consumer [key secret]
  (oauth/make-consumer
     key secret
     (str constants/+drbx-oauth-host+ "request_token/")
     (str constants/+drbx-oauth-host+ "access_token/")
     (str constants/+drbx-oauth-host+ "authorize/")
     :hmac-sha1))

(defn request-token [oauth-consumer]
  (oauth/request-token oauth-consumer))

(defn approval-uri [consumer oauth-token callback-uri]
  (format "%s/authorize?%s"
	  constants/+drbx-oauth-host+
	  (http/encode-query	   
	   (merge {"oauth_token" oauth-token}
		  (if callback-uri
		    {"oauth_callback" callback-uri} {})))))

(defn access-token-response
  ([consumer request-token callback-uri]  
     (http/get  (str constants/+drbx-oauth-host+ "/access_token")
		:query (merge {:oauth_token (:oauth_token request-token)
			       :oauth_consumer_key (:key consumer)}
			      (if callback-uri {:oauth_callback callback-uri} {}))
		:parameters (http/map->params {:use-expect-continue false})
		:as :string))
  ([consumer request-token] (access-token-response consumer request-token nil)))

(defn  make-connection [consumer user-key user-secret  opts]
  {
   :req-fn
    (fn [http-method request-url params & others]
      (let [credentials (oauth/credentials consumer user-key user-secret
					  http-method request-url params)
	   f (case http-method
		   :GET  http/get
		   :PUT  http/put
		   :DELETE http/delete
		   :POST http/post)]
       (if (= http-method :POST)
	 (http/post request-url
		    :query (merge credentials params)
		    :parameters (http/map->params {:use-expect-continue false})
		    :body (:BODY (into {} (map vec (partition 2 others))))
		    :as :string)
	 (f request-url
	    :query (merge credentials params)
	    :parameters (http/map->params {:use-expect-continue false})
	    :as :string))))
    :opts opts
   })