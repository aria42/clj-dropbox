# OAuth support for Clojure #

`clj-dropbox`  provides clojure bindings to the [Dropbox API](http://developer.dropbox.com).
In order to use this library, you need to at least setup as a Dropbox developer and 
get an OAuth conumser key. Instructions can be found [here](https://www.dropbox.com/developers/quickstart). 

The library depends upon `clj-oauth'. There was a bug in the original project, so I'm working
off of [daniel42](http://github.com/daniel42)'s which I've uploaded to [clojars.org](http://clojars.org). 

# Building #

`lein jar`

# Example #

    (require '[clj-dropbox.client :as dropbox])

    ; First you make a `consumer' which represents your 
    ; Dropbox  credientials you need to interact with Dropbox's OAuth. 
    (def consumer (dropbox/new-consumer "my-developer-key" "my-developer-secret"))

    ; You need to do the OAuth dance in order
    ; to get the user-key and user-secret. You
    ; should persistently store these. 
    (let [[authorization-url request-callback] 
             (dropbox/request-user-authorization consumer my-callback-url)]
      ; User grants you access
      (println "Dear user authorize me at " authorization-url)
      ; After this is done and my-callback-url is pinged
      (let [[user-key user-secret] (request-callback)]
        (println "I have user token info!")))  

    ; Once you have user-key and user-secret, make a client 
    (def client (dropbox/new-user-clien consumer user-key user-secret))

    ; Now you can do the fun stuff (see src/clj-dropbox/client.clj
    ; to see all operations"

    ; Upload local file to dropbox
    (upload-file client "/path/to-local-file.txt" /dropbox-dir/)

    ; List files in root dropbox director
    ; returns seq of file meta-data 
    (list-files client "")

    ; Get the contents of a file as a string
    (get-file client "tmp/my-great-idea.txt")

    ; If you want to do several operations with the same user
    ; on the same thread, you can save yourself the trouble
    ; of using the client argument everywhere
    (dropbox/with-user (new-user-client user-key user-secret)
      (for [[i f] (indexed (dropbox/list-files "")) 
            :when (= (-> f :path (.endsWith ".txt")))]
        (copy-file (:path f) (str "my-text-files/txt-file-" i))))
    

# Author #
Aria Haghighi (aria42@gmail.com)
http://csail.mit.edu/~aria42

# Support #

Email author with any issues.