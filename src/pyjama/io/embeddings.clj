(ns pyjama.io.embeddings
  (:require [mikera.vectorz.core :as vectorz]
            [taoensso.nippy :as nippy])
  (:import (java.io DataInputStream DataOutputStream FileInputStream FileOutputStream)
           (mikera.vectorz Vector)))

;; Register custom serialization for vectorz.Vector
(nippy/extend-freeze Vector ::vectorz-vector
                     [x data-output]
                       (nippy/freeze-to-out! data-output (.asDoubleArray x))) ;; Store as a double arra

(nippy/extend-thaw ::vectorz-vector
                   [data-input]
                     (vectorz/vec (nippy/thaw-from-in! data-input))) ;; Convert back to Vector
;(def nippy-options {:allow-unsafe-serialization? true})

(defn save-documents [filename docs]
  (with-open [out (DataOutputStream. (FileOutputStream. ^String filename))]
    (nippy/freeze-to-out! out docs)))

(defn load-documents [filename]
  (with-open [in (DataInputStream. (FileInputStream. ^String filename))]
    (nippy/thaw-from-in! in)))
