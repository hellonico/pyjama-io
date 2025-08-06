(ns reader_test
      (:require [pyjama.io.readers :refer [extract-text]]
                [pyjama.io.core :as pyo]
                [clojure.test :refer :all]))

(deftest test-pdf-with-tables
  (println
    (->
      "https://www.w3.org/WAI/WCAG20/Techniques/working-examples/PDF20/table.pdf"
      (pyo/download-file)
      (extract-text))))

(def -vercingetorix-page
 "https://www.lemonde.fr/series-d-ete/article/2025/08/03/comment-vercingetorix-est-devenu-le-premier-heros-du-roman-national_6626432_3451060.html")

(deftest test-html
 (println
  (->
   -vercingetorix-page
   ;(pyo/download-file)
   (extract-text))))