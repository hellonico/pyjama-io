(ns extract-tables
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [pyjama.io.core :as pyo])
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (technology.tabula ObjectExtractor)
           (technology.tabula.extractors BasicExtractionAlgorithm SpreadsheetExtractionAlgorithm)))

(defn extract-tables
  "Extracts tables from a PDF and prints them in a structured format."
  [pdf-file]
  (with-open [document (PDDocument/load (io/file pdf-file))]
    (let [extractor (ObjectExtractor. document)
          sea (SpreadsheetExtractionAlgorithm.)
          ;sea (BasicExtractionAlgorithm.)
          ]
      (doseq [page (iterator-seq (.extract extractor))]
        (println (class page))
        (doseq [[i table] (map-indexed vector (.extract sea page))]
          ;(doseq [table (.extract sea page)]
          (println "table " (.getPageNumber page) "-" i)
          (doseq [row (.getRows table)]
            ;(println "--------------------")
            (println
              (clojure.string/join ","
                                   (map #(->> (.getTextElements %)
                                              (map (fn [t] (.getText t))) ;; Extracts each chunk separately
                                              (clojure.string/join " ")) row)))))))))


(deftest hello
  (println
    (->
      "https://www.w3.org/WAI/WCAG20/Techniques/working-examples/PDF20/table.pdf"
      (pyo/download-file)
      (extract-tables))))