(ns pyjama.io.parallelmap
  "Helpers to read CSV/Excel grids and construct parameter maps for parallel runs.

  - read-csv/read-excel: load data as sequences of rows.
  - load-data: dispatch by file extension (csv, xls, xlsx).
  - construct-map: expects headers like \"models\", \"prompts\", optional \"pre\" and \"system\";
    returns {:models [...], :prompts [...], :pre \"...\"?, :system \"...\"?} suitable for downstream tasks."
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [dk.ative.docjure.spreadsheet :as ss]))

(defn read-csv [file]
  (with-open [reader (io/reader file)]
    (doall (csv/read-csv reader))))

(defn read-excel [file]
  (let [workbook (ss/load-workbook file)
        sheet (ss/select-sheet workbook 0)]
    (map (fn [row] (mapv #(ss/read-cell-value %) row)) (ss/row-seq sheet))))

(defn load-data [file]
  (let [extension (-> file .getName (clojure.string/split #"\.") last)]
    (cond
      (= extension "csv") (read-csv file)
      (or (= extension "xls") (= extension "xlsx")) (read-excel file)
      :else (throw (ex-info "Unsupported file format" {:file file})))))

(defn construct-map [excel-data]
  (let [headers (first excel-data)
        data (rest excel-data)
        headers-index (zipmap headers (range))

        models (->> (map #(get % (headers-index "models")) data)
                    (filter #(not (empty? %)))
                    (into []))
        prompts (->> (map #(get % (headers-index "prompts")) data)
                     (filter #(not (empty? %))))
        pre (some->> (map #(get % (headers-index "pre")) data) first )
        system (some->> (map #(get % (headers-index "system")) data) first )
        ]
    (cond-> {:models models :prompts prompts}
            pre (assoc :pre pre)
            system (assoc :system system))))