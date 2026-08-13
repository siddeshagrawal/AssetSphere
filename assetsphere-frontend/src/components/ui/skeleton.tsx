import type {HTMLAttributes} from "react";
import {cn} from "@/lib/utils";

type SkeletonProps = HTMLAttributes<HTMLDivElement>;

function Skeleton({className, ...props}: SkeletonProps) {
    return (
        <div
            className={cn("skeleton-shimmer rounded-md", className)}
            aria-hidden="true"
            {...props}
        />
    );
}

export {Skeleton};
