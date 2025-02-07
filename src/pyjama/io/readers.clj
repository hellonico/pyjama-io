(ns pyjama.io.readers
  (:require [clojure.java.io :as io])
  (:import (java.io FileInputStream)
           (java.io FileInputStream)
           (nl.siegmann.epublib.epub EpubReader)
           (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.text PDFTextStripper)
           (org.apache.poi.xwpf.extractor XWPFWordExtractor)
           (org.apache.poi.xwpf.usermodel XWPFDocument)
           (org.jsoup Jsoup)))

(defn extract-pdf-text [file-path]
  (with-open [doc (PDDocument/load (io/file file-path))]
    (let [stripper (PDFTextStripper.)]
      (.getText stripper doc))))

(defn extract-docx-text [file-path]
  (with-open [fis (io/input-stream file-path)
              doc (XWPFDocument. fis)
              extractor (XWPFWordExtractor. doc)]
    (.getText extractor)))

(defn extract-epub-text [file-path]
  (with-open [fis (FileInputStream. (io/file file-path))]
    (let [book (.readEpub (EpubReader.) fis)
          spine-items (-> book .getSpine .getSpineReferences)
          extract-content (fn [item]
                            (let [stream (-> item .getResource .getInputStream)
                                  html (slurp stream)
                                  parsed-html (Jsoup/parse html)
                                  paragraphs (map #(str (.text %)) (.select parsed-html "p"))] ;; Extract paragraphs
                              (clojure.string/join "\n" paragraphs))) ;; Join paragraphs with newlines
          extracted-text (map extract-content spine-items)]
      (clojure.string/join "\n\n" extracted-text))))

(defn extract-text [file-path]
  (let [handlers {#{"pdf"}        extract-pdf-text
                  #{"epub"}       extract-epub-text
                  ;#{"md"}         -> slurp
                  #{"doc" "docx"} extract-docx-text}
        ext (some #(if (some (fn [x] (clojure.string/ends-with? file-path x)) %) %) (keys handlers))]
    (if ext
      ((get handlers ext slurp) file-path)
      (slurp file-path))))